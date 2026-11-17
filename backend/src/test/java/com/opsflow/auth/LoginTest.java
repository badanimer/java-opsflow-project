package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Login and the lockout (T-20, T-28), through HTTP calls against the MySQL container. The rules are in
 * Login security and account recovery in docs/database/database-design.md.
 */
class LoginTest extends AuthIntegrationSupport {

	private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

	/** Midday in Sydney, far from the midnight at which the count starts again. */
	private static final Instant MIDDAY = ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, SYDNEY).toInstant();

	@Test
	void theRightPasswordGetsATokenThatOpensTheAccount() throws Exception {
		clock.set(MIDDAY);
		long alice = account("alice");

		String body = login("alice", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expiresAt").value("2026-11-13T13:00:00Z"))
				.andReturn().getResponse().getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

		me(token).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(alice))
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.roles", hasSize(0)));
		assertThat(lastLoginAt(alice)).isEqualTo(MIDDAY);
	}

	@Test
	void aWrongPasswordAndAnUnknownUsernameGetTheSameAnswer() throws Exception {
		long alice = account("alice");

		for (String username : new String[] { "alice", "nobody" }) {
			login(username, WRONG)
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
					.andExpect(jsonPath("$.detail").value("Wrong username or password"))
					.andExpect(jsonPath("$.requestId").isNotEmpty())
					.andExpect(jsonPath("$.token").doesNotExist());
		}
		assertThat(failedLoginCount(alice)).isEqualTo(1);
	}

	@Test
	void theSystemIdentityCannotLogInAndTheAttemptIsNotCounted() throws Exception {
		long system = root().queryForObject("SELECT id FROM users WHERE is_system", Long.class);
		String marker = root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, system);

		login("system", marker).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		login("system", WRONG).andExpect(status().isUnauthorized());

		assertThat(failedLoginCount(system)).isZero();
		assertThat(lastLoginAt(system)).isNull();
	}

	@Test
	void aSoftDeletedAccountIsAnsweredLikeAnUnknownUser() throws Exception {
		long alice = account("alice");
		root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", alice);

		login("alice", PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		login("alice", WRONG).andExpect(status().isUnauthorized());

		assertThat(failedLoginCount(alice)).isZero();
	}

	@Test
	void theFifthFailureLocksTheAddressOutForAMinuteAndALockedAddressIsRefusedWithoutACheck() throws Exception {
		clock.set(MIDDAY);
		long alice = account("alice");

		for (int i = 0; i < 5; i++) {
			login("alice", WRONG).andExpect(status().isUnauthorized());
		}
		assertThat(addressFailures(alice, HOME)).isEqualTo(5);
		assertThat(addressLockedUntil(alice, HOME)).isEqualTo(MIDDAY.plus(Duration.ofMinutes(1)));
		// The failures of the day count in the user row too, and lock the account itself only at the ceiling.
		assertThat(failedLoginCount(alice)).isEqualTo(5);
		assertThat(lockedUntil(alice)).isNull();

		// Locked: even the right password is refused, the answer says until when, and nothing is counted.
		login("alice", PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
				.andExpect(jsonPath("$.lockedUntil").value("2026-11-13T01:01:00Z"));
		login("alice", WRONG).andExpect(status().isTooManyRequests());
		assertThat(addressFailures(alice, HOME)).isEqualTo(5);
		assertThat(failedLoginCount(alice)).isEqualTo(5);

		// The lock is logged once, with nobody as the operator, because nobody is logged in, and names the address.
		Map<String, Object> log = root().queryForMap(
				"SELECT operator_id, category, target_type, ip_address, remark, CAST(after_data AS CHAR) AS after_data FROM operation_logs WHERE action = 'LOGIN_LOCKED' AND target_id = ?",
				alice);
		assertThat(log.get("operator_id")).isNull();
		assertThat(log.get("category")).isEqualTo("ACCOUNT");
		assertThat(log.get("target_type")).isEqualTo("USER");
		assertThat(log.get("ip_address")).isEqualTo(HOME);
		assertThat(log.get("remark")).isEqualTo(LoginRecorder.ADDRESS_LOCKED);
		assertThat((String) log.get("after_data")).contains("\"failedAttempts\": 5", "\"username\": \"alice\"",
				"\"lockedUntil\": \"2026-11-13T01:01:00Z\"", "\"lockedAddress\": \"127.0.0.1\"");

		// When the minute is over, the right password works and clears the count of this address.
		clock.advance(Duration.ofSeconds(61));
		login("alice", PASSWORD).andExpect(status().isOk());
		assertThat(addressFailures(alice, HOME)).isZero();
		assertThat(addressLockedUntil(alice, HOME)).isNull();
	}

	/** The point of counting by address: a stranger who types wrong passwords locks out only themselves. */
	@Test
	void aStrangerWhoLocksTheirOwnAddressDoesNotLockTheOwnerOut() throws Exception {
		clock.set(MIDDAY);
		long alice = account("alice");

		for (int i = 0; i < 20; i++) {
			loginFrom("203.0.113.66", "alice", WRONG);
			clock.advance(Duration.ofMinutes(16));
		}
		loginFrom("203.0.113.66", "alice", PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

		loginFrom("10.0.0.7", "alice", PASSWORD).andExpect(status().isOk());
		assertThat(addressLockedUntil(alice, "10.0.0.7")).isNull();
		assertThat(lockedUntil(alice)).isNull();
	}

	@Test
	void theLockOfAnAddressGrowsAtTenFifteenAndTwentyFailures() throws Exception {
		clock.set(MIDDAY);
		long alice = account("alice");
		Instant nextMidnight = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, SYDNEY).toInstant();

		for (int failure = 1; failure <= 20; failure++) {
			Instant now = clock.instant();
			login("alice", WRONG).andExpect(status().isUnauthorized());
			assertThat(addressFailures(alice, HOME)).isEqualTo(failure);
			assertThat(failedLoginCount(alice)).isEqualTo(failure);

			Instant expected = switch (failure) {
				case 5 -> now.plus(Duration.ofMinutes(1));
				case 10 -> now.plus(Duration.ofMinutes(5));
				case 15 -> now.plus(Duration.ofMinutes(15));
				case 20 -> nextMidnight;
				default -> null;
			};
			if (expected != null) {
				assertThat(addressLockedUntil(alice, HOME)).as("after failure %s", failure).isEqualTo(expected);
			}
			if (expected != null && failure < 20) {
				clock.set(expected.plusSeconds(1));
			}
		}
		assertThat(lockRows(alice)).isEqualTo(4);

		// Locked until midnight, and the new day starts clean.
		clock.set(nextMidnight.minusSeconds(1));
		login("alice", PASSWORD).andExpect(status().isTooManyRequests());
		clock.set(nextMidnight.plusSeconds(1));
		login("alice", WRONG).andExpect(status().isUnauthorized());
		assertThat(addressFailures(alice, HOME)).isEqualTo(1);
		assertThat(failedLoginCount(alice)).isEqualTo(1);
	}

	@Test
	void theCountsStartAgainAtMidnightInSydney() throws Exception {
		Instant midnight = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, SYDNEY).toInstant();
		long alice = account("alice");

		clock.set(midnight.minus(Duration.ofMinutes(10)));
		for (int i = 0; i < 4; i++) {
			login("alice", WRONG).andExpect(status().isUnauthorized());
		}
		assertThat(failedLoginCount(alice)).isEqualTo(4);

		// The fifth wrong password comes after midnight, so it is the first of the new day and locks nothing.
		clock.set(midnight.plus(Duration.ofMinutes(10)));
		login("alice", WRONG).andExpect(status().isUnauthorized());
		assertThat(failedLoginCount(alice)).isEqualTo(1);
		assertThat(addressFailures(alice, HOME)).isEqualTo(1);
		assertThat(addressLockedUntil(alice, HOME)).isNull();
	}

	@Test
	void theRightPasswordClearsTheCountOfItsAddressButNotTheFailuresOfTheDay() throws Exception {
		long alice = account("alice");
		for (int i = 0; i < 4; i++) {
			login("alice", WRONG).andExpect(status().isUnauthorized());
		}

		login("alice", PASSWORD).andExpect(status().isOk());

		assertThat(addressFailures(alice, HOME)).isZero();
		// One more wrong password is the first of this address again, so it locks nothing.
		login("alice", WRONG).andExpect(status().isUnauthorized());
		assertThat(addressLockedUntil(alice, HOME)).isNull();
		assertThat(failedLoginCount(alice)).isEqualTo(5);
	}

	/** Guessing from many addresses: the failures of the day from every address lock the account itself. */
	@Test
	void theFailuresOfTheDayFromEveryAddressLockTheAccountUntilMidnightAtTheCeiling() throws Exception {
		clock.set(MIDDAY);
		long alice = account("alice");
		Instant nextMidnight = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, SYDNEY).toInstant();
		root().update("UPDATE users SET failed_login_count = 99, last_failed_login_at = ? WHERE id = ?",
				com.opsflow.common.time.UtcTime.toLocalDateTime(MIDDAY.minusSeconds(60)), alice);

		loginFrom("198.51.100.4", "alice", WRONG).andExpect(status().isUnauthorized());

		assertThat(failedLoginCount(alice)).isEqualTo(100);
		assertThat(lockedUntil(alice)).isEqualTo(nextMidnight);
		Map<String, Object> log = root().queryForMap(
				"SELECT remark, CAST(after_data AS CHAR) AS after_data FROM operation_logs WHERE action = 'LOGIN_LOCKED' AND target_id = ?", alice);
		assertThat(log.get("remark")).isEqualTo(LoginRecorder.ACCOUNT_LOCKED);
		assertThat((String) log.get("after_data")).contains("\"failedAttempts\": 100").doesNotContain("lockedAddress");

		// Every address is refused now, the owner's too, until midnight or a password reset.
		loginFrom("10.0.0.7", "alice", PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
		clock.set(nextMidnight.plusSeconds(1));
		loginFrom("10.0.0.7", "alice", PASSWORD).andExpect(status().isOk());
	}

	/** Trying many usernames from one address: the address is refused for the rest of the window. */
	@Test
	void anAddressWithTooManyFailuresOnAnyUsernameIsRefusedUntilTheWindowEnds() throws Exception {
		clock.set(MIDDAY);
		account("alice");
		String address = "192.0.2.9";
		for (int i = 0; i < 49; i++) {
			loginFrom(address, "nobody" + i, WRONG).andExpect(status().isUnauthorized());
		}
		loginFrom(address, "alice", WRONG).andExpect(status().isUnauthorized());

		// The fiftieth failure of the window: the address is refused before anything else, whatever it sends.
		loginFrom(address, "alice", PASSWORD).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		loginFrom(address, "nobody", WRONG).andExpect(status().isTooManyRequests());
		loginFrom("10.0.0.7", "alice", PASSWORD).andExpect(status().isOk());

		// The window is 15 minutes long; the next one starts clean.
		clock.advance(Duration.ofMinutes(15));
		loginFrom(address, "alice", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void aDisabledAccountWithTheRightPasswordGetsAccountDisabledAndNoToken() throws Exception {
		long alice = account("alice");
		login("alice", WRONG).andExpect(status().isUnauthorized());
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);

		login("alice", PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
				.andExpect(jsonPath("$.detail").value("This account has been disabled. Please contact an administrator."))
				.andExpect(jsonPath("$.token").doesNotExist());

		// It is not a successful login: the count and the time of the last login stay as they were.
		assertThat(failedLoginCount(alice)).isEqualTo(1);
		assertThat(lastLoginAt(alice)).isNull();
	}

	@Test
	void aDisabledAccountWithAWrongPasswordGetsTheGeneralErrorAndTheFailureCounts() throws Exception {
		long alice = account("alice");
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);

		login("alice", WRONG)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.detail").value("Wrong username or password"));

		assertThat(failedLoginCount(alice)).isEqualTo(1);
	}

	@Test
	void aRequestWithAMissingOrOverlongFieldIsRefusedBeforeAnyCheck() throws Exception {
		long alice = account("alice");

		login("", PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		login("alice", "").andExpect(status().isBadRequest());
		login("alice", "x".repeat(25)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"));
		login("a".repeat(65), PASSWORD).andExpect(status().isBadRequest());

		assertThat(failedLoginCount(alice)).isZero();
	}

	@Test
	void aSuccessfulLoginAndASingleWrongPasswordWriteNoLogRow() throws Exception {
		account("alice");

		login("alice", WRONG).andExpect(status().isUnauthorized());
		login("alice", PASSWORD).andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"));

		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class)).isZero();
	}

}
