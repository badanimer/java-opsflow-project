package com.opsflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * The two transactions that an email code allows: creating an account, and setting a new password (see
 * Email codes and Login steps in docs/database/database-design.md).
 *
 * The code was checked, and the password hashed, before either transaction starts. Each one uses the code
 * up with a conditional update, so of two requests with the same code only one goes on, and writes its
 * audit row. Lock order: {@code users}, then {@code email_verification_codes}. Sign-up takes the code
 * row before it inserts the user; that row of {@code users} is new, so nobody can be waiting for it.
 */
@Component
public class AccountWriter {

	/** A new account, the issue time of its first token, and the id of that first login. */
	public record Created(long id, Instant issuedAt, String sessionId) {
	}

	private final AuthRepository repository;
	private final EmailCodeRepository codes;
	private final AuditLogWriter audit;
	private final int maxWrongEntries;
	private final Clock clock;

	AccountWriter(AuthRepository repository, EmailCodeRepository codes, AuditLogWriter audit, OpsflowProperties properties,
			Clock clock) {
		this.repository = repository;
		this.codes = codes;
		this.audit = audit;
		this.maxWrongEntries = properties.emailCode().maxWrongEntries();
		this.clock = clock;
	}

	/**
	 * Creates the account: active, with no role. Every refusal is thrown, so the transaction rolls back
	 * and the code is still unused: a person whose username is taken picks another one and sends the same
	 * code again.
	 *
	 * The new user is logged in at once, so the transaction also gives the issue time of the first token
	 * and stores it as {@code last_login_at}: a later password reset is then never earlier than that token.
	 * It also stores the id of this first login as {@code session_id}, which the token carries.
	 *
	 * @return the id of the new user, the issue time of its first token, and the id of the login
	 */
	@Transactional
	public Created create(long codeId, String username, String email, String passwordHash, String ipAddress) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (repository.usernameExists(username)) {
			throw usernameTaken();
		}
		if (repository.findByEmail(email).isPresent()) {
			throw emailTaken();
		}
		if (!codes.consume(codeId, now, maxWrongEntries)) {
			throw EmailCodeService.invalidCode();
		}
		String sessionId = SessionIds.newId();
		long id;
		try {
			id = repository.insertUser(username, email, passwordHash, now, sessionId);
		}
		catch (DuplicateKeyException sameMoment) {
			// Another sign-up took the name or the address after the two reads above. The unique keys decide.
			throw String.valueOf(sameMoment.getMessage()).contains("uk_users_email") ? emailTaken() : usernameTaken();
		}
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("username", username);
		after.put("email", email);
		// The new user is the operator of their own sign-up.
		audit.write(AuditAction.USER_REGISTERED, id, AuditLogWriter.TARGET_USER, id, null, after, ipAddress, null);
		return new Created(id, now, sessionId);
	}

	/**
	 * Sets the new password, clears the lock, and sets {@code password_changed_at}, with the user row
	 * locked, so a login in progress sees either the old row or the new one (the other side of the race
	 * in {@link LoginRecorder}).
	 *
	 * @param checkedHash the hash the new password was compared with, outside this transaction
	 * @return false when the code can no longer be used or the account changed in the meantime; nothing
	 *     is written then
	 */
	@Transactional
	public boolean resetPassword(long codeId, long userId, String checkedHash, String newHash, String ipAddress) {
		Account account = repository.lockById(userId).orElse(null);
		// Read after the lock is held, so it is not earlier than what the transaction before this one wrote.
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (account == null || account.deleted() || account.system() || !account.passwordHash().equals(checkedHash)) {
			return false;
		}
		if (!codes.consume(codeId, now, maxWrongEntries)) {
			return false;
		}
		Instant changedAt = changeTime(account, now);
		repository.resetPassword(userId, newHash, changedAt);

		Map<String, Object> after = new LinkedHashMap<>();
		after.put("passwordChangedAt", changedAt.toString());
		after.put("lockCleared", true);
		after.put("username", account.username());
		audit.write(AuditAction.PASSWORD_RESET, userId, AuditLogWriter.TARGET_USER, userId, null, after, ipAddress, null);
		return true;
	}

	/**
	 * A token is refused unless it was issued after {@code password_changed_at}, so the new value must not
	 * be earlier than the issue time of any token of the old password. A login records its time in
	 * {@code last_login_at}, and a login in the same millisecond as the reset before it is issued one
	 * millisecond after that reset. The new value is therefore at least both.
	 */
	private static Instant changeTime(Account account, Instant now) {
		Instant changedAt = now;
		if (account.lastLoginAt() != null && account.lastLoginAt().isAfter(changedAt)) {
			changedAt = account.lastLoginAt();
		}
		if (account.passwordChangedAt() != null && !changedAt.isAfter(account.passwordChangedAt())) {
			changedAt = account.passwordChangedAt().plusMillis(1);
		}
		return changedAt;
	}

	private static ApiException usernameTaken() {
		return new ApiException(ErrorCode.USERNAME_TAKEN, "This username is already used. Choose another one.");
	}

	static ApiException emailTaken() {
		return new ApiException(ErrorCode.EMAIL_TAKEN, "This email address already has an account.");
	}

}
