package com.opsflow.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * What the tests of the user module share: accounts and roles made directly in the database, a login
 * through the real endpoint, and the calls of the user endpoints through the whole application.
 */
@AutoConfigureMockMvc
abstract class UserIntegrationSupport extends MySqlIntegrationTest {

	static final String PASSWORD = "correct-horse-1";

	/** The lowest cost, so that a test with many logins stays fast. The application reads the cost from the hash. */
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

	@Autowired
	MockMvc mvc;

	@Autowired
	UserService users;

	/** An active account whose password is {@link #PASSWORD}, with these roles. */
	long account(String username, String... roleCodes) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, PASSWORD_HASH,
				username + "@example.com");
		long id = root().queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
		for (String code : roleCodes) {
			root().update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, code);
		}
		return id;
	}

	void disable(long userId) {
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", userId);
	}

	void softDelete(long userId) {
		root().update("UPDATE users SET status = 'DISABLED', deleted = TRUE WHERE id = ?", userId);
	}

	long systemIdentity() {
		return root().queryForObject("SELECT id FROM users WHERE is_system = TRUE", Long.class);
	}

	/** Logs in and returns the token. */
	String tokenOf(String username) throws Exception {
		String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
		String answer = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(answer, "$.token");
	}

	/** {@code PUT /users/{id}/roles} with the roles wanted and the roles seen. */
	ResultActions replaceRoles(String token, long userId, List<String> roles, List<String> expectedRoles) throws Exception {
		String body = "{\"roles\":" + json(roles) + ",\"expectedRoles\":" + json(expectedRoles) + "}";
		return mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** {@code POST /users/{id}/disable}. */
	ResultActions disableUser(String token, long userId) throws Exception {
		return mvc.perform(post("/api/v1/users/" + userId + "/disable").header("Authorization", "Bearer " + token));
	}

	/** {@code POST /users/{id}/enable}. */
	ResultActions enableUser(String token, long userId) throws Exception {
		return mvc.perform(post("/api/v1/users/" + userId + "/enable").header("Authorization", "Bearer " + token));
	}

	/** {@code DELETE /users/{id}}. */
	ResultActions deleteUser(String token, long userId) throws Exception {
		return mvc.perform(delete("/api/v1/users/" + userId).header("Authorization", "Bearer " + token));
	}

	String statusOf(long userId) {
		return root().queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
	}

	boolean isDeleted(long userId) {
		return root().queryForObject("SELECT deleted FROM users WHERE id = ?", Boolean.class, userId);
	}

	/** The account log rows about a user, oldest first. */
	List<Map<String, Object>> accountLogs(long userId) {
		return root().queryForList("""
				SELECT action, category, operator_id, target_type, CAST(before_data AS CHAR) AS before_data,
				       CAST(after_data AS CHAR) AS after_data, ip_address, remark
				FROM operation_logs
				WHERE target_id = ? AND action IN ('USER_STATUS_CHANGED', 'USER_DELETED', 'PASSWORD_RESET')
				ORDER BY id
				""", userId);
	}

	/** The JSON of a log column as a map. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> data(Map<String, Object> log, String column) {
		return JsonPath.parse((String) log.get(column)).read("$", Map.class);
	}

	/** The user as a request of theirs would carry it. The roles are read again in the transaction, so they are left empty. */
	static CurrentUser actor(long id) {
		return new CurrentUser(id, "actor-" + id, List.of());
	}

	List<String> rolesOf(long userId) {
		return root().queryForList(
				"SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.code",
				String.class, userId);
	}

	/** The role log rows about a user, oldest first. */
	List<Map<String, Object>> roleLogs(long userId) {
		return root().queryForList("""
				SELECT action, category, operator_id, target_type, CAST(before_data AS CHAR) AS before_data,
				       CAST(after_data AS CHAR) AS after_data, ip_address, remark
				FROM operation_logs
				WHERE target_id = ? AND action IN ('ROLE_GRANTED', 'ROLE_REVOKED')
				ORDER BY id
				""", userId);
	}

	int usableAdministrators() {
		return root().queryForObject("""
				SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id JOIN users u ON u.id = ur.user_id
				WHERE r.code = 'ADMIN' AND u.status = 'ACTIVE' AND u.deleted = FALSE
				""", Integer.class);
	}

	private static String json(List<String> values) {
		return values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(",", "[", "]"));
	}

	static List<String> roles(String... codes) {
		return Arrays.asList(codes);
	}

}
