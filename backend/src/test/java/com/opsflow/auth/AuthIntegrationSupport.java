package com.opsflow.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.mail.OutgoingMail;
import com.opsflow.common.time.UtcTime;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * What the tests of login, sign-up, and the password reset share: HTTP calls through the whole
 * application, the security filters included, the code from the mail that the application sent, and
 * accounts made directly in the database where a test is not about sign-up.
 */
@AutoConfigureMockMvc
abstract class AuthIntegrationSupport extends MySqlIntegrationTest {

	static final String PASSWORD = "correct-horse-1";
	static final String WRONG = "wrong-horse-1";

	/** The lowest cost, so that a test with many logins stays fast. The application reads the cost from the hash. */
	private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

	private static final Pattern SIX_DIGITS = Pattern.compile("\\b\\d{6}\\b");

	@Autowired
	MockMvc mvc;

	@Autowired
	AccountWriter accountWriter;

	static String hash(String password) {
		return "{bcrypt}" + BCRYPT.encode(password);
	}

	/** An active account with no role, whose password is {@link #PASSWORD}. */
	long account(String username) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, hash(PASSWORD),
				username + "@example.com");
		return root().queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
	}

	void grant(long userId, String roleCode) {
		root().update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", userId, roleCode);
	}

	/**
	 * Resets the password with the real transaction of the application ({@link AccountWriter#resetPassword}),
	 * without the HTTP call and the mail: the code row is made here. A race test calls this in the middle
	 * of a login, where a second HTTP call would go through the password hasher that the test has wrapped.
	 */
	void resetPassword(long userId, String newPassword) {
		String email = root().queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
		String currentHash = root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
		root().update("INSERT INTO email_verification_codes (email, purpose, code_hash, expires_at, created_at) VALUES (?, 'RESET_PASSWORD', 'made-by-the-test', ?, ?)",
				email, UtcTime.now(clock).plusMinutes(10), UtcTime.now(clock));
		long codeId = root().queryForObject("SELECT MAX(id) FROM email_verification_codes WHERE email = ?", Long.class, email);
		if (!accountWriter.resetPassword(codeId, userId, currentHash, hash(newPassword), null)) {
			throw new IllegalStateException("The reset was refused");
		}
	}

	ResultActions requestCode(String email, String purpose) throws Exception {
		return requestCode(email, purpose, "127.0.0.1");
	}

	/** Requests an email code as the client at this address. */
	ResultActions requestCode(String email, String purpose, String ipAddress) throws Exception {
		String body = "{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}";
		return mvc.perform(post("/api/v1/auth/email-codes").contentType(MediaType.APPLICATION_JSON).content(body).with(request -> {
			request.setRemoteAddr(ipAddress);
			return request;
		}));
	}

	/** Requests a code and returns the one in the mail. */
	String codeFor(String email, String purpose) throws Exception {
		requestCode(email, purpose).andExpect(status().isNoContent());
		return codeMailedTo(email);
	}

	/** The code in the latest mail to this address. */
	String codeMailedTo(String email) {
		List<OutgoingMail> mails = sentMail().stream().filter(mail -> mail.to().equals(email)).toList();
		if (mails.isEmpty()) {
			throw new AssertionError("No mail was sent to " + email);
		}
		Matcher code = SIX_DIGITS.matcher(mails.get(mails.size() - 1).text());
		if (!code.find()) {
			throw new AssertionError("The mail holds no code");
		}
		return code.group();
	}

	/** A 6-digit code that is not this one. */
	static String otherThan(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	ResultActions signUp(String username, String email, String password, String code) throws Exception {
		String body = "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password
				+ "\",\"code\":\"" + code + "\"}";
		return mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	ResultActions resetPasswordWith(String email, String code, String newPassword) throws Exception {
		String body = "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}";
		return mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** The latest code row of an address and purpose. */
	Map<String, Object> codeRow(String email, String purpose) {
		return root().queryForMap(
				"SELECT id, code_hash, attempts, expires_at, consumed_at, created_at FROM email_verification_codes WHERE email = ? AND purpose = ? ORDER BY id DESC LIMIT 1",
				email, purpose);
	}

	int wrongEntries(String email, String purpose) {
		return ((Number) codeRow(email, purpose).get("attempts")).intValue();
	}

	/** The accepted code requests counted for a scope ({@code ALL}, or {@code IP:} and the address), over all windows. */
	int requestCount(String scope) {
		return root().queryForObject("SELECT COALESCE(SUM(request_count), 0) FROM email_code_request_counts WHERE scope = ?",
				Integer.class, scope);
	}

	int auditRows(String action, long userId) {
		return root().queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action = ? AND target_id = ?", Integer.class,
				action, userId);
	}

	ResultActions login(String username, String password) throws Exception {
		String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Logs in and returns the token. */
	String tokenOf(String username, String password) throws Exception {
		String body = login(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	ResultActions me(String token) throws Exception {
		return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
	}

	Map<String, Object> userRow(long userId) {
		return root().queryForMap(
				"SELECT failed_login_count, last_failed_login_at, locked_until, last_login_at, password_changed_at FROM users WHERE id = ?",
				userId);
	}

	int failedLoginCount(long userId) {
		return ((Number) userRow(userId).get("failed_login_count")).intValue();
	}

	Instant lockedUntil(long userId) {
		return UtcTime.toInstant((LocalDateTime) userRow(userId).get("locked_until"));
	}

	Instant lastLoginAt(long userId) {
		return UtcTime.toInstant((LocalDateTime) userRow(userId).get("last_login_at"));
	}

	int lockRows(long userId) {
		return root().queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action = 'LOGIN_LOCKED' AND target_id = ?",
				Integer.class, userId);
	}

}
