package com.opsflow.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

class UtcTimeTest {

	@Test
	void aDatetimeColumnValueIsReadAsUtc() {
		assertThat(UtcTime.toInstant(LocalDateTime.of(2026, 11, 4, 3, 0, 0))).isEqualTo(Instant.parse("2026-11-04T03:00:00Z"));
	}

	@Test
	void aTimeIsStoredAsUtcWallTimeCutToMilliseconds() {
		Instant instant = Instant.parse("2026-11-04T03:00:00.123456789Z");

		assertThat(UtcTime.toLocalDateTime(instant)).isEqualTo(LocalDateTime.of(2026, 11, 4, 3, 0, 0, 123_000_000));
	}

	@Test
	void theRoundTripKeepsTheInstantToTheMillisecond() {
		Instant instant = Instant.parse("2026-11-04T03:00:00.123Z");

		assertThat(UtcTime.toInstant(UtcTime.toLocalDateTime(instant))).isEqualTo(instant);
	}

	@Test
	void nullStaysNull() {
		assertThat(UtcTime.toInstant(null)).isNull();
		assertThat(UtcTime.toLocalDateTime(null)).isNull();
	}

	@Test
	void theResultDoesNotDependOnTheDefaultTimeZoneOfTheMachine() {
		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"));
			Clock fixed = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Australia/Sydney"));

			assertThat(UtcTime.now(fixed)).isEqualTo(LocalDateTime.of(2026, 11, 4, 3, 0));
			assertThat(UtcTime.toInstant(LocalDateTime.of(2026, 11, 4, 3, 0))).isEqualTo(LocalDateTime.of(2026, 11, 4, 3, 0).toInstant(ZoneOffset.UTC));
		}
		finally {
			TimeZone.setDefault(original);
		}
	}

}
