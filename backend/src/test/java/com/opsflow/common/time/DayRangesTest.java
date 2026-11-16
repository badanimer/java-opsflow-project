package com.opsflow.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.time.DayRanges.Range;

/** The time filter (see The time filter): days in Sydney time, turned into a UTC range, and every refusal. */
class DayRangesTest {

	/** 2026-11-16 at noon in Sydney, which is 01:00 UTC (daylight saving time, UTC+11). */
	private static final Instant NOON_IN_SYDNEY = Instant.parse("2026-11-16T01:00:00Z");

	private final DayRanges days = new DayRanges(Clock.fixed(NOON_IN_SYDNEY, ZoneOffset.UTC), new OpsflowProperties(null, null, null, null,
			null, null, null, null, null, null, new OpsflowProperties.TimeFilter(ZoneId.of("Australia/Sydney")), null));

	@Test
	void noDayIsNoFilter() {
		assertThat(days.parse(null, null).isNone()).isTrue();
	}

	@Test
	void aStartDayAloneRunsFromTheStartOfThatDayInSydneyUntilNow() {
		Range range = days.parse("2026-11-16", null);
		assertThat(range.from()).isEqualTo(LocalDateTime.parse("2026-11-15T13:00"));
		assertThat(range.to()).isNull();
	}

	@Test
	void twoDaysIncludeBothAndEndAtTheStartOfTheDayAfterTheLast() {
		Range range = days.parse("2026-11-10", "2026-11-16");
		assertThat(range.from()).isEqualTo(LocalDateTime.parse("2026-11-09T13:00"));
		assertThat(range.to()).isEqualTo(LocalDateTime.parse("2026-11-16T13:00"));
		Range oneDay = days.parse("2026-11-16", "2026-11-16");
		assertThat(oneDay.to()).isEqualTo(LocalDateTime.parse("2026-11-16T13:00"));
	}

	@Test
	void aDayInWinterIsTenHoursAheadOfUtcAndTheDayOfTheChangeIsShorter() {
		assertThat(days.parse("2026-07-01", "2026-07-01").from()).isEqualTo(LocalDateTime.parse("2026-06-30T14:00"));
		Range change = days.parse("2026-10-04", "2026-10-04");
		assertThat(change.from()).isEqualTo(LocalDateTime.parse("2026-10-03T14:00"));
		assertThat(change.to()).as("the clocks go forward that night: 23 hours").isEqualTo(LocalDateTime.parse("2026-10-04T13:00"));
	}

	@Test
	void anEndDayWithoutAStartDayADayAfterTodayAndAnEndBeforeTheStartAreRefused() {
		refused(() -> days.parse(null, "2026-11-16"), "from");
		refused(() -> days.parse("2026-11-17", null), "from");
		refused(() -> days.parse("2026-11-10", "2026-11-17"), "to");
		refused(() -> days.parse("2026-11-12", "2026-11-11"), "to");
	}

	@Test
	void aDayThatIsNotWrittenAsYearMonthDayIsRefused() {
		refused(() -> days.parse("16/11/2026", null), "from");
		refused(() -> days.parse("2026-11-01", "yesterday"), "to");
		refused(() -> days.parse("2026-02-30", null), "from");
	}

	private static void refused(Runnable call, String field) {
		assertThatThrownBy(call::run).isInstanceOf(ApiException.class).satisfies(e -> {
			ApiException refusal = (ApiException) e;
			assertThat(refusal.code().name()).isEqualTo("VALIDATION_FAILED");
			assertThat(String.valueOf(refusal.extras().get("errors"))).contains(field);
		});
	}

}
