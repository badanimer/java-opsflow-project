package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.support.RaceRunner;

/**
 * The password reset through HTTP, against the MySQL container: what it changes, what it refuses, and
 * its side of the race with a login (see Login steps in docs/database/database-design.md). The login
 * side is in {@link LoginRaceTest}.
 */
class PasswordResetTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final String EMAIL = "alice@example.com";
	private static final String NEW_PASSWORD = "new-horse-2";

	@Test
	void aResetSetsTheNewPasswordAndEndsTheTokensOfTheOldOne() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String oldToken = tokenOf("alice", PASSWORD);
		clock.advance(Duration.ofMinutes(1));
		String code = codeFor(EMAIL, "RESET_PASSWORD");

		resetPasswordWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""));

		login("alice", PASSWORD).andExpect(status().isUnauthorized());
		me(oldToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		me(tokenOf("alice", NEW_PASSWORD)).andExpect(status().isOk());
		assertThat(userRow(alice).get("password_changed_at")).isEqualTo(LocalDateTime.of(2026, 11, 13, 1, 1));
		assertThat(codeRow(EMAIL, "RESET_PASSWORD").get("consumed_at")).isNotNull();

		// The user is the operator of the log row, and the row holds no password, code, or hash.
		Map<String, Object> log = root().queryForMap(
				"SELECT operator_id, category, CAST(after_data AS CHAR) AS after_data FROM operation_logs WHERE action = 'PASSWORD_RESET' AND target_id = ?",
				alice);
		assertThat(((Number) log.get("operator_id")).longValue()).isEqualTo(alice);
		assertThat(log.get("category")).isEqualTo("ACCOUNT");
		assertThat((String) log.get("after_data")).contains("2026-11-13T01:01:00Z").contains("lockCleared")
				.doesNotContain(NEW_PASSWORD).doesNotContain(code).doesNotContain("bcrypt");
	}

	@Test
	void aResetClearsTheLocksOfFailedLoginsAtEveryAddress() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		for (String address : new String[] { HOME, "10.0.0.7" }) {
			for (int i = 0; i < 5; i++) {
				loginFrom(address, "alice", WRONG).andExpect(status().isUnauthorized());
			}
			loginFrom(address, "alice", PASSWORD).andExpect(status().isTooManyRequests());
		}

		resetPasswordWith(EMAIL, codeFor(EMAIL, "RESET_PASSWORD"), NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(failedLoginCount(alice)).isZero();
		assertThat(lockedUntil(alice)).isNull();
		assertThat(addressLockedUntil(alice, HOME)).isNull();
		assertThat(addressFailures(alice, "10.0.0.7")).isZero();
		login("alice", NEW_PASSWORD).andExpect(status().isOk());
		loginFrom("10.0.0.7", "alice", NEW_PASSWORD).andExpect(status().isOk());
	}

	/** The code is right, so the person may be told. The code is not used up. */
	@Test
	void theNewPasswordMustDifferFromTheCurrentOne() throws Exception {
		account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");

		resetPasswordWith(EMAIL, code, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("newPassword"));

		assertThat(codeRow(EMAIL, "RESET_PASSWORD").get("consumed_at")).isNull();
		resetPasswordWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	@Test
	void aWeakNewPasswordIsRefusedBeforeTheCodeIsLookedAt() throws Exception {
		account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");

		resetPasswordWith(EMAIL, otherThan(code), "onlyletters")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("newPassword"));

		assertThat(wrongEntries(EMAIL, "RESET_PASSWORD")).isZero();
		login("alice", PASSWORD).andExpect(status().isOk());
	}

	/** An address with no account behaves like one with an account and a wrong code. */
	@Test
	void aWrongCodeIsRefusedAndCountedWhetherOrNotTheAddressHasAnAccount() throws Exception {
		account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");
		requestCode("nobody@example.com", "RESET_PASSWORD").andExpect(status().isNoContent());

		String known = resetPasswordWith(EMAIL, otherThan(code), NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID")).andReturn().getResponse().getContentAsString();
		String unknown = resetPasswordWith("nobody@example.com", "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID")).andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(unknown, "$.detail")).isEqualTo(JsonPath.read(known, "$.detail"));
		assertThat(wrongEntries(EMAIL, "RESET_PASSWORD")).isEqualTo(1);
		assertThat(wrongEntries("nobody@example.com", "RESET_PASSWORD")).isEqualTo(1);
		// An address that never asked for a code gets the same answer.
		resetPasswordWith("never@example.com", "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));
		login("alice", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aSignUpCodeDoesNotResetAPassword() throws Exception {
		account("alice");
		String code = codeFor("new@example.com", "REGISTER");

		resetPasswordWith("new@example.com", code, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));
		resetPasswordWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));
	}

	/** A disabled account gets a new password and stays disabled. */
	@Test
	void aDisabledAccountStaysDisabledAfterAReset() throws Exception {
		long alice = account("alice");
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);

		resetPasswordWith(EMAIL, codeFor(EMAIL, "RESET_PASSWORD"), NEW_PASSWORD).andExpect(status().isNoContent());

		login("alice", NEW_PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
	}

	/** The account was soft-deleted after the code was mailed. */
	@Test
	void anAccountDeletedInTheMeantimeIsRefused() throws Exception {
		long alice = account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");
		String hashBefore = root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, alice);
		root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", alice);

		resetPasswordWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALID"));

		assertThat(root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, alice)).isEqualTo(hashBefore);
		assertThat(auditRows("PASSWORD_RESET", alice)).isZero();
	}

	/**
	 * The clock stands still, so the login, the reset, the next login, and the next reset all fall in the
	 * same millisecond. A token of the old password must still end with each reset.
	 */
	@Test
	void aTokenIssuedInTheSameMillisecondAsTheResetIsRefused() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String first = tokenOf("alice", PASSWORD);

		resetPasswordWith(EMAIL, codeFor(EMAIL, "RESET_PASSWORD"), NEW_PASSWORD).andExpect(status().isNoContent());

		me(first).andExpect(status().isUnauthorized());
		// The next login is issued one millisecond after the reset, so it works.
		String second = tokenOf("alice", NEW_PASSWORD);
		me(second).andExpect(status().isOk());

		// A second reset in that millisecond. A new code cannot be requested for 60 seconds, so the helper
		// calls the transaction of the reset directly.
		resetPassword(alice, "newer-horse-3");

		me(second).andExpect(status().isUnauthorized());
		me(tokenOf("alice", "newer-horse-3")).andExpect(status().isOk());
	}

	/**
	 * A login with the old password and the reset run at the same moment, several times. Whichever commits
	 * first, nobody stays logged in with the old password: the login is refused, or its token is.
	 */
	@Test
	void aLoginWithTheOldPasswordNeverSurvivesAResetAtTheSameMoment() throws Exception {
		for (int round = 0; round < 5; round++) {
			String name = "user" + round;
			String email = name + "@example.com";
			account(name);
			String code = codeFor(email, "RESET_PASSWORD");
			AtomicInteger next = new AtomicInteger();

			List<RaceRunner.Outcome<String>> outcomes = RaceRunner.run(2, () -> {
				if (next.getAndIncrement() == 0) {
					return login(name, PASSWORD).andReturn().getResponse().getContentAsString();
				}
				resetPasswordWith(email, code, NEW_PASSWORD).andExpect(status().isNoContent());
				return null;
			});

			assertThat(outcomes).as("round %d", round).allMatch(RaceRunner.Outcome::succeeded);
			for (RaceRunner.Outcome<String> outcome : outcomes) {
				if (outcome.value() != null && outcome.value().contains("\"token\"")) {
					me(JsonPath.read(outcome.value(), "$.token")).andExpect(status().isUnauthorized());
				}
			}
			login(name, PASSWORD).andExpect(status().isUnauthorized());
			login(name, NEW_PASSWORD).andExpect(status().isOk());
		}
	}

	@Test
	void oneCodeSentTwiceAtTheSameMomentResetsOnce() throws Exception {
		long alice = account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(4,
				() -> resetPasswordWith(EMAIL, code, NEW_PASSWORD).andReturn().getResponse().getStatus());

		assertThat(outcomes).allMatch(RaceRunner.Outcome::succeeded);
		assertThat(outcomes).extracting(RaceRunner.Outcome::value).filteredOn(status -> status == 204).hasSize(1);
		// The others found the code used, or found the new password already set.
		assertThat(outcomes).extracting(RaceRunner.Outcome::value).allMatch(status -> status == 204 || status == 400);
		assertThat(auditRows("PASSWORD_RESET", alice)).isEqualTo(1);
		login("alice", NEW_PASSWORD).andExpect(status().isOk());
	}

}
