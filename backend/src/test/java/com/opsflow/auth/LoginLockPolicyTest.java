package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;

/** The lockout rules of T-28 and Q-07, without a database or Redis. */
class LoginLockPolicyTest {

	private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

	private final LoginLockPolicy policy = new LoginLockPolicy(new OpsflowProperties.Login(
			Map.of(5, Duration.ofMinutes(1), 10, Duration.ofMinutes(5), 15, Duration.ofMinutes(15)), 20, SYDNEY, 100, 50,
			Duration.ofMinutes(15)));

	private static Instant sydney(int month, int day, int hour, int minute) {
		return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, SYDNEY).toInstant();
	}

	@Test
	void onlyTheFifthTenthAndFifteenthFailureOfAnAddressLockItOutForATime() {
		Instant now = sydney(11, 13, 12, 0);

		for (int failures = 1; failures < 20; failures++) {
			Duration expected = switch (failures) {
				case 5 -> Duration.ofMinutes(1);
				case 10 -> Duration.ofMinutes(5);
				case 15 -> Duration.ofMinutes(15);
				default -> null;
			};
			assertThat(policy.addressLockAfter(failures, now)).as("failure %s", failures)
					.isEqualTo(java.util.Optional.ofNullable(expected).map(now::plus));
		}
	}

	@Test
	void fromTheTwentiethFailureTheAddressIsLockedOutUntilMidnightInSydney() {
		Instant midnight = sydney(11, 14, 0, 0);

		assertThat(policy.addressLockAfter(20, sydney(11, 13, 12, 0))).contains(midnight);
		assertThat(policy.addressLockAfter(21, sydney(11, 13, 23, 59))).contains(midnight);
		// One second after midnight it is the next midnight.
		assertThat(policy.addressLockAfter(20, midnight.plusSeconds(1))).contains(sydney(11, 15, 0, 0));
	}

	@Test
	void theAccountItselfIsLockedUntilMidnightOnlyAtTheCeilingOfTheDay() {
		Instant now = sydney(11, 13, 12, 0);

		assertThat(policy.accountLockAfter(99, now)).isEmpty();
		assertThat(policy.accountLockAfter(100, now)).contains(sydney(11, 14, 0, 0));
	}

	@Test
	void aCountWhoseLastFailureWasBeforeTheLastMidnightIsReadAsZero() {
		Instant midnight = sydney(11, 14, 0, 0);

		assertThat(policy.failuresToday(4, midnight.minusSeconds(1), midnight.plusSeconds(1))).isZero();
		assertThat(policy.failuresToday(4, midnight, midnight.plusSeconds(1))).isEqualTo(4);
		assertThat(policy.failuresToday(4, midnight.minusSeconds(2), midnight.minusSeconds(1))).isEqualTo(4);
		assertThat(policy.failuresToday(0, null, midnight)).isZero();
	}

	@Test
	void theDayOfTheCountOfAnAddressIsTheDayInSydney() {
		assertThat(policy.day(Instant.parse("2026-11-13T12:59:00Z"))).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(policy.day(Instant.parse("2026-11-13T13:01:00Z"))).isEqualTo(LocalDate.of(2026, 11, 14));
	}

	@Test
	void theWindowsOfAnAddressFollowEachOtherAndEachEndsWhereTheNextBegins() {
		Instant start = Instant.parse("2026-11-13T01:00:00Z");

		assertThat(policy.addressWindow(start.plusSeconds(899))).isEqualTo(policy.addressWindow(start));
		assertThat(policy.addressWindow(start.plusSeconds(900))).isEqualTo(policy.addressWindow(start) + 1);
		assertThat(policy.addressWindowEnd(start.plusSeconds(10))).isEqualTo(start.plusSeconds(900));
	}

	/** Midnight in Sydney is 13:00 UTC in summer time and 14:00 UTC in winter time, not midnight UTC. */
	@Test
	void midnightIsTakenInSydneyAndNotInUtc() {
		assertThat(policy.addressLockAfter(20, Instant.parse("2026-11-13T12:59:00Z"))).contains(Instant.parse("2026-11-13T13:00:00Z"));
		assertThat(policy.addressLockAfter(20, Instant.parse("2026-11-13T13:01:00Z"))).contains(Instant.parse("2026-11-14T13:00:00Z"));
		assertThat(policy.addressLockAfter(20, Instant.parse("2026-06-13T13:01:00Z"))).contains(Instant.parse("2026-06-13T14:00:00Z"));
	}

	/** On 4 October 2026 the clocks in Sydney go forward, and on 5 April 2026 they go back. */
	@Test
	void aDayWithAChangeOfDaylightSavingTimeHasTwentyThreeOrTwentyFiveHours() {
		Instant shortDay = sydney(10, 4, 0, 0);
		Instant longDay = sydney(4, 5, 0, 0);

		assertThat(Duration.between(shortDay, policy.addressLockAfter(20, shortDay).orElseThrow())).isEqualTo(Duration.ofHours(23));
		assertThat(Duration.between(longDay, policy.addressLockAfter(20, longDay).orElseThrow())).isEqualTo(Duration.ofHours(25));
	}

}
