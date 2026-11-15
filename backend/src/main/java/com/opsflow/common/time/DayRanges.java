package com.opsflow.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;

/**
 * The time filter of every list that is filtered by time (see The time filter in
 * docs/database/database-design.md). A person picks days in the display time zone, and this class turns
 * them into a UTC range for the query:
 * <ul>
 * <li>no day: no filter;</li>
 * <li>a start day and an end day, both included: from the start of the first day to the start of the day
 * after the last;</li>
 * <li>a start day alone: from the start of that day until now, with no upper end.</li>
 * </ul>
 * An end day without a start day, a day that is not {@code yyyy-MM-dd}, a day after today, and an end day
 * before the start day are refused with {@code VALIDATION_FAILED}.
 */
@Component
public class DayRanges {

	/**
	 * A UTC range of {@code DATETIME} values: {@code from} included, {@code to} excluded. Either may be null:
	 * both for no filter, {@code to} for "until now".
	 */
	public record Range(LocalDateTime from, LocalDateTime to) {

		public static final Range NONE = new Range(null, null);

		public boolean isNone() {
			return from == null && to == null;
		}
	}

	private final Clock clock;
	private final ZoneId zone;

	public DayRanges(Clock clock, OpsflowProperties properties) {
		this.clock = clock;
		this.zone = properties.timeFilter().zone();
	}

	/**
	 * @param from the {@code from} parameter, or null
	 * @param to the {@code to} parameter, or null
	 */
	public Range parse(String from, String to) {
		if (from == null && to == null) {
			return Range.NONE;
		}
		if (from == null) {
			throw ApiException.invalid("from", "is required with to");
		}
		LocalDate today = clock.instant().atZone(zone).toLocalDate();
		LocalDate first = day("from", from);
		if (first.isAfter(today)) {
			throw ApiException.invalid("from", "must not be after today");
		}
		if (to == null) {
			return new Range(startOf(first), null);
		}
		LocalDate last = day("to", to);
		if (last.isAfter(today)) {
			throw ApiException.invalid("to", "must not be after today");
		}
		if (last.isBefore(first)) {
			throw ApiException.invalid("to", "must not be before from");
		}
		return new Range(startOf(first), startOf(last.plusDays(1)));
	}

	private LocalDateTime startOf(LocalDate day) {
		return UtcTime.toLocalDateTime(day.atStartOfDay(zone).toInstant());
	}

	private static LocalDate day(String field, String text) {
		try {
			return LocalDate.parse(text);
		}
		catch (DateTimeParseException e) {
			throw ApiException.invalid(field, "must be a day written as yyyy-MM-dd");
		}
	}

}
