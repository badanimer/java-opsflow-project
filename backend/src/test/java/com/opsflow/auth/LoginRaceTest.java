package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.opsflow.support.RaceRunner;

/**
 * The races of login in Concurrency by Module (docs/database/database-design.md), against the MySQL
 * container.
 *
 * The password is checked outside the transaction that records the result, so something can commit in
 * between. These tests make that happen at exactly that point: the real {@link PasswordHasher} is
 * wrapped, and right after it has checked the password, and before the login opens its transaction, the
 * test commits the other change. No timing is involved, so the order is the same on every run.
 */
class LoginRaceTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final String NEW_PASSWORD = "new-horse-2";

	@MockitoSpyBean
	PasswordHasher hasher;

	/** Runs {@code change} once, after the next password check has given its answer. */
	private void afterThePasswordCheck(Runnable change) {
		doAnswer(invocation -> {
			Object matched = invocation.callRealMethod();
			change.run();
			return matched;
		}).doCallRealMethod().when(hasher).matches(anyString(), anyString());
	}

	/** The old password has passed the check, but the reset commits first. */
	@Test
	void aLoginThatCheckedTheOldPasswordIsRefusedWhenAResetCommitsFirst() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		afterThePasswordCheck(() -> resetPassword(alice, NEW_PASSWORD));

		login("alice", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.detail").value("Wrong username or password"))
				.andExpect(jsonPath("$.token").doesNotExist());

		// Nothing is recorded: the row is as the reset left it.
		assertThat(failedLoginCount(alice)).isZero();
		assertThat(lockedUntil(alice)).isNull();
		assertThat(lastLoginAt(alice)).isNull();
		// And the passwords are what the reset made them.
		login("alice", NEW_PASSWORD).andExpect(status().isOk());
	}

	/** The check failed against the old hash, which says nothing about the new password. */
	@Test
	void aWrongPasswordIsNotCountedWhenAResetCommitsFirst() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		root().update("UPDATE users SET failed_login_count = 4, last_failed_login_at = ? WHERE id = ?",
				java.time.LocalDateTime.of(2026, 11, 13, 0, 59), alice);
		// The person types the new password a moment before the reset commits.
		afterThePasswordCheck(() -> resetPassword(alice, NEW_PASSWORD));

		login("alice", NEW_PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		// The reset cleared the count, and this attempt did not make it 1 or lock the account at 5.
		assertThat(failedLoginCount(alice)).isZero();
		assertThat(lockedUntil(alice)).isNull();
		assertThat(lockRows(alice)).isZero();
	}

	@Test
	void aResetThatCommitsAfterTheLoginMakesItsTokenUseless() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);
		me(token).andExpect(status().isOk());

		// The clock stands still: the reset is in the same millisecond as the login.
		resetPassword(alice, NEW_PASSWORD);

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void aLoginIsRefusedWhenTheAccountIsDisabledAfterThePasswordCheck() throws Exception {
		long alice = account("alice");
		afterThePasswordCheck(() -> root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice));

		login("alice", PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
				.andExpect(jsonPath("$.token").doesNotExist());

		assertThat(lastLoginAt(alice)).isNull();
	}

	@Test
	void aLoginIsRefusedWhenTheAccountIsSoftDeletedAfterThePasswordCheck() throws Exception {
		long alice = account("alice");
		afterThePasswordCheck(() -> root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", alice));

		login("alice", PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		assertThat(lastLoginAt(alice)).isNull();
	}

	/** Other attempts locked the account while this one was checking its password. */
	@Test
	void aLoginIsRefusedAsLockedWhenTheAccountWasLockedAfterThePasswordCheck() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		afterThePasswordCheck(() -> root().update(
				"UPDATE users SET failed_login_count = 5, last_failed_login_at = ?, locked_until = ? WHERE id = ?",
				java.time.LocalDateTime.of(2026, 11, 13, 1, 0), java.time.LocalDateTime.of(2026, 11, 13, 1, 1), alice));

		login("alice", PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

		assertThat(failedLoginCount(alice)).isEqualTo(5);
		assertThat(lastLoginAt(alice)).isNull();
	}

	/** Five threads, released together, each with a wrong password: every one counts, and the fifth locks. */
	@Test
	void wrongPasswordsAtTheSameMomentAreAllCountedAndLockTheAccountOnce() {
		clock.set(NOW);
		long alice = account("alice");

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(5,
				() -> login("alice", WRONG).andReturn().getResponse().getStatus());

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.value()).as("outcome %s", outcome).isEqualTo(401));
		assertThat(failedLoginCount(alice)).isEqualTo(5);
		assertThat(lockedUntil(alice)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		assertThat(lockRows(alice)).isEqualTo(1);
	}

}
