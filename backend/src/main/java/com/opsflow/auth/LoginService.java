package com.opsflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 * <li>Refuse a client address that has had its failures in this window ({@link LoginLimits}).</li>
 * <li>Read the user with no transaction. An unknown user, a soft-deleted account, and the SYSTEM identity
 * get the general login error. An account locked for every address, or this address locked out of the
 * account, is refused without a password check.</li>
 * <li>Check the password, still with no transaction, because the check is slow.</li>
 * <li>{@link LoginRecorder} locks the user row, confirms that the hash that was checked is still the
 * current one, and records the result. A success writes a new session id, which ends every earlier
 * login of the account; after the commit, {@link SessionEvents} tells their open pages.</li>
 * <li>After the commit, count a wrong password for this address in Redis, and log the lock of the address when
 * the count reaches a step; or clear the count of this address after a success.</li>
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
	private final LoginLimits limits;
	private final TransactionRetry retry;
	private final TokenService tokens;
	private final SessionEvents sessionEvents;
	private final Clock clock;
	/** Checked when there is no account to check, so that the answer takes as long as for a real one. */
	private final String decoyHash;

	LoginService(AuthRepository repository, PasswordHasher hasher, LoginRecorder recorder, LoginLimits limits, TransactionRetry retry,
			TokenService tokens, SessionEvents sessionEvents, Clock clock) {
		this.repository = repository;
		this.limits = limits;
		this.hasher = hasher;
		this.recorder = recorder;
		this.retry = retry;
		this.tokens = tokens;
		this.sessionEvents = sessionEvents;
		this.clock = clock;
		this.decoyHash = hasher.hash(UUID.randomUUID().toString());
	}

	public IssuedToken login(String username, String password, String ipAddress) {
		// An address that has tried too many usernames or passwords in this window is refused before anything
		// else, so its answer says nothing about the username.
		if (limits.addressRefusedUntil(ipAddress).isPresent()) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "Too many failed logins from this address. Try again later.");
		}
		Account account = repository.findByUsername(username).orElse(null);
		if (account == null || account.deleted() || account.system()) {
			hasher.matches(password, decoyHash);
			limits.recordAddressFailure(ipAddress);
			throw wrongUsernameOrPassword();
		}
		if (account.lockedUntil() != null && account.lockedUntil().isAfter(clock.instant())) {
			throw locked(account.lockedUntil());
		}
		Optional<Instant> addressLock = limits.lockedUntil(account.id(), ipAddress);
		if (addressLock.isPresent()) {
			throw locked(addressLock.get());
		}

		boolean matched = hasher.matches(password, account.passwordHash());

		Outcome outcome = retry.run(() -> recorder.record(account.id(), account.passwordHash(), matched, ipAddress));
		return switch (outcome.kind()) {
			case SUCCESS -> {
				limits.clear(account.id(), ipAddress);
				// The login has committed, so the earlier logins of the account are already refused by the
				// token check. Their open pages are told now.
				sessionEvents.loggedIn(account.id(), outcome.sessionId());
				yield tokens.issue(account.id(), outcome.issuedAt(), outcome.sessionId());
			}
			case DISABLED -> throw new ApiException(ErrorCode.ACCOUNT_DISABLED, DISABLED_MESSAGE);
			case LOCKED -> throw locked(outcome.lockedUntil());
			case WRONG_PASSWORD -> {
				// The failure of the day has committed in the user row; now the counts of this address.
				limits.recordAddressFailure(ipAddress);
				limits.recordFailure(account.id(), ipAddress).filter(failure -> failure.lockedUntil() != null).ifPresent(
						failure -> retry.run(() -> {
							recorder.recordAddressLock(account.id(), account.username(), failure.failures(), failure.lockedUntil(), ipAddress);
							return null;
						}));
				throw wrongUsernameOrPassword();
			}
			case VOID -> throw wrongUsernameOrPassword();
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
