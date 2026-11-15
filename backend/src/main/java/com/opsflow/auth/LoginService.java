package com.opsflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.auth.LoginRecorder.Outcome;
import com.opsflow.auth.TokenService.IssuedToken;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.retry.TransactionRetry;

/**
 * The login (T-20, T-28). The steps are those of Login steps in docs/database/database-design.md:
 *
 * <ol>
 * <li>Read the user with no transaction. An unknown user, a soft-deleted account, and the SYSTEM identity
 * get the general login error. A locked account is refused without a password check.</li>
 * <li>Check the password, still with no transaction, because the check is slow.</li>
 * <li>{@link LoginRecorder} locks the user row, confirms that the hash that was checked is still the
 * current one, and records the result. A success writes a new session id, which ends every earlier
 * login of the account; after the commit, {@link SessionEvents} tells their open pages.</li>
 * </ol>
 *
 * This class has no transaction of its own. The answer never says whether the username exists: every
 * refusal that is not about a lock or a disabled account is the same {@code UNAUTHENTICATED}.
 */
@Service
public class LoginService {

	static final String DISABLED_MESSAGE = "This account has been disabled. Please contact an administrator.";

	private final AuthRepository repository;
	private final PasswordHasher hasher;
	private final LoginRecorder recorder;
	private final TransactionRetry retry;
	private final TokenService tokens;
	private final SessionEvents sessionEvents;
	private final Clock clock;
	/** Checked when there is no account to check, so that the answer takes as long as for a real one. */
	private final String decoyHash;

	LoginService(AuthRepository repository, PasswordHasher hasher, LoginRecorder recorder, TransactionRetry retry,
			TokenService tokens, SessionEvents sessionEvents, Clock clock) {
		this.repository = repository;
		this.hasher = hasher;
		this.recorder = recorder;
		this.retry = retry;
		this.tokens = tokens;
		this.sessionEvents = sessionEvents;
		this.clock = clock;
		this.decoyHash = hasher.hash(UUID.randomUUID().toString());
	}

	public IssuedToken login(String username, String password, String ipAddress) {
		Account account = repository.findByUsername(username).orElse(null);
		if (account == null || account.deleted() || account.system()) {
			hasher.matches(password, decoyHash);
			throw wrongUsernameOrPassword();
		}
		if (account.lockedUntil() != null && account.lockedUntil().isAfter(clock.instant())) {
			throw locked(account.lockedUntil());
		}

		boolean matched = hasher.matches(password, account.passwordHash());

		Outcome outcome = retry.run(() -> recorder.record(account.id(), account.passwordHash(), matched, ipAddress));
		return switch (outcome.kind()) {
			case SUCCESS -> {
				// The login has committed, so the earlier logins of the account are already refused by the
				// token check. Their open pages are told now.
				sessionEvents.loggedIn(account.id(), outcome.sessionId());
				yield tokens.issue(account.id(), outcome.issuedAt(), outcome.sessionId());
			}
			case DISABLED -> throw new ApiException(ErrorCode.ACCOUNT_DISABLED, DISABLED_MESSAGE);
			case LOCKED -> throw locked(outcome.lockedUntil());
			case WRONG_PASSWORD, VOID -> throw wrongUsernameOrPassword();
		};
	}

	private static ApiException wrongUsernameOrPassword() {
		return new ApiException(ErrorCode.UNAUTHENTICATED, "Wrong username or password");
	}

	private static ApiException locked(Instant lockedUntil) {
		return new ApiException(ErrorCode.LOGIN_LOCKED, "Too many failed logins. Try again later.").with("lockedUntil",
				lockedUntil);
	}

}
