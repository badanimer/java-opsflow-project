package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;

/** The lockout rules of T-28, without a database. */
class LoginLockPolicyTest {

	private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

	private final LoginLockPolicy policy = new LoginLockPolicy(new OpsflowProperties.Login(
			Map.of(5, Duration.ofMinutes(1), 10, Duration.ofMinutes(5), 15, Duration.ofMinutes(15)), 20, SYDNEY));

	private static Instant sydney(int month, int day, int hour, int minute) {
		return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, SYDNEY).toInstant();
	}

	@Test
	void onlyTheFifthTenthAndFifteenthFailureLockForATime() {
		Instant now = sydney(11, 13, 12, 0);

		for (int failures = 1; failures < 20; failures++) {
			Duration expected = switch (failures) {
				case 5 -> Duration.ofMinutes(1);
				case 10 -> Duration.ofMinutes(5);
				case 15 -> Duration.ofMinutes(15);
				default -> null;
			};
			assertThat(policy.lockAfter(failures, now)).as("failure %s", failures)
					.isEqualTo(java.util.Optional.ofNullable(expected).map(now::plus));
		}
	}

	@Test
	void fromTheTwentiethFailureTheAccountIsLockedUntilMidnightInSydney() {
		Instant midnight = sydney(11, 14, 0, 0);

		assertThat(policy.lockAfter(20, sydney(11, 13, 12, 0))).contains(midnight);
		assertThat(policy.lockAfter(21, sydney(11, 13, 23, 59))).contains(midnight);
		// One second after midnight it is the next midnight.
		assertThat(policy.lockAfter(20, midnight.plusSeconds(1))).contains(sydney(11, 15, 0, 0));
	}

	@Test
	void aCountWhoseLastFailureWasBeforeTheLastMidnightIsReadAsZero() {
		Instant midnight = sydney(11, 14, 0, 0);

		assertThat(policy.failuresInARow(4, midnight.minusSeconds(1), midnight.plusSeconds(1))).isZero();
		assertThat(policy.failuresInARow(4, midnight, midnight.plusSeconds(1))).isEqualTo(4);
		assertThat(policy.failuresInARow(4, midnight.minusSeconds(2), midnight.minusSeconds(1))).isEqualTo(4);
		assertThat(policy.failuresInARow(0, null, midnight)).isZero();
	}

	/** Midnight in Sydney is 13:00 UTC in summer time and 14:00 UTC in winter time, not midnight UTC. */
	@Test
	void midnightIsTakenInSydneyAndNotInUtc() {
		assertThat(policy.lockAfter(20, Instant.parse("2026-11-13T12:59:00Z"))).contains(Instant.parse("2026-11-13T13:00:00Z"));
		assertThat(policy.lockAfter(20, Instant.parse("2026-11-13T13:01:00Z"))).contains(Instant.parse("2026-11-14T13:00:00Z"));
		assertThat(policy.lockAfter(20, Instant.parse("2026-06-13T13:01:00Z"))).contains(Instant.parse("2026-06-13T14:00:00Z"));
	}

	/** On 4 October 2026 the clocks in Sydney go forward, and on 5 April 2026 they go back. */
	@Test
	void aDayWithAChangeOfDaylightSavingTimeHasTwentyThreeOrTwentyFiveHours() {
		Instant shortDay = sydney(10, 4, 0, 0);
		Instant longDay = sydney(4, 5, 0, 0);

		assertThat(Duration.between(shortDay, policy.lockAfter(20, shortDay).orElseThrow())).isEqualTo(Duration.ofHours(23));
		assertThat(Duration.between(longDay, policy.lockAfter(20, longDay).orElseThrow())).isEqualTo(Duration.ofHours(25));
	}

}
