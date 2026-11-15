package com.opsflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;

/**
 * Step 3 of the login: the short transaction that records the result of a password check (see Login
 * steps in docs/database/database-design.md).
 *
 * The password was checked outside this transaction, against a hash that was read without a lock. A
 * password reset, a disabling, or other failed attempts may have committed since. So the user row is
 * locked here and read again, and a result is recorded only when the hash that was checked is still the
 * current one. Lock order: {@code users} only.
 *
 * The method returns the outcome and never throws a refusal: an exception would roll the transaction
 * back, and a failed attempt must stay counted. {@link LoginService} turns the outcome into the answer
 * after the commit.
 */
@Component
public class LoginRecorder {

	enum Kind {
		/** The hash changed or the account is gone: the check says nothing about the current password. */
		VOID,
		/** Other attempts locked the account in the meantime. */
		LOCKED,
		WRONG_PASSWORD,
		DISABLED,
		SUCCESS
	}

	/**
	 * @param lockedUntil for {@code LOCKED}, when the lock ends
	 * @param issuedAt for {@code SUCCESS}, the issue time of the token
	 * @param sessionId for {@code SUCCESS}, the id of this login, which ends every earlier login of the account
	 */
	record Outcome(Kind kind, Instant lockedUntil, Instant issuedAt, String sessionId) {

		static Outcome of(Kind kind) {
			return new Outcome(kind, null, null, null);
		}
	}

	private final AuthRepository repository;
	private final LoginLockPolicy lockPolicy;
	private final AuditLogWriter audit;
	private final Clock clock;

	LoginRecorder(AuthRepository repository, LoginLockPolicy lockPolicy, AuditLogWriter audit, Clock clock) {
		this.repository = repository;
		this.lockPolicy = lockPolicy;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public Outcome record(long userId, String checkedHash, boolean passwordMatched, String ipAddress) {
		Account account = repository.lockById(userId).orElse(null);
		// The time is read after the lock is held, so it is not earlier than anything that a transaction
		// which held the lock before has written.
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);

		if (account == null || account.deleted() || account.system() || !account.passwordHash().equals(checkedHash)) {
			return Outcome.of(Kind.VOID);
		}
		if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
			return new Outcome(Kind.LOCKED, account.lockedUntil(), null, null);
		}
		if (!passwordMatched) {
			recordFailure(account, now, ipAddress);
			return Outcome.of(Kind.WRONG_PASSWORD);
		}
		if (account.disabled()) {
			return Outcome.of(Kind.DISABLED);
		}
		String sessionId = SessionIds.newId();
		repository.recordSuccess(account.id(), now, sessionId);
		return new Outcome(Kind.SUCCESS, null, issueTime(account, now), sessionId);
	}

	private void recordFailure(Account account, Instant now, String ipAddress) {
		int failures = lockPolicy.failuresInARow(account.failedLoginCount(), account.lastFailedLoginAt(), now) + 1;
		Instant lockedUntil = lockPolicy.lockAfter(failures, now).orElse(null);
		repository.recordFailure(account.id(), failures, now, lockedUntil);
		if (lockedUntil != null) {
			Map<String, Object> after = new LinkedHashMap<>();
			after.put("failedAttempts", failures);
			after.put("lockedUntil", lockedUntil.toString());
			after.put("username", account.username());
			// Nobody is logged in, so the row has no operator.
			audit.write(AuditAction.LOGIN_LOCKED, null, AuditLogWriter.TARGET_USER, account.id(), null, after, ipAddress, null);
		}
	}

	/**
	 * A token is refused unless it was issued after {@code password_changed_at}. A login in the same
	 * millisecond as the reset that came before it therefore gets the next millisecond as its issue time.
	 */
	private static Instant issueTime(Account account, Instant now) {
		Instant changed = account.passwordChangedAt();
		return changed != null && !now.isAfter(changed) ? changed.plusMillis(1) : now;
	}

}
