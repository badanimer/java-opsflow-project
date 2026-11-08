package com.opsflow.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Time in the application is UTC (T-09; see Time zone in docs/database/database-design.md).
 *
 * <ul>
 * <li>Take "now" from the {@link Clock} bean, never from {@code LocalDateTime.now()} or another call that
 * reads the default time zone. A test can then fix the time.</li>
 * <li>A {@code DATETIME(3)} column holds a UTC wall time. Read it with {@code rs.getObject(column,
 * LocalDateTime.class)} and convert it with {@link #toInstant}; write it with {@link #toLocalDateTime}.</li>
 * <li>The API sends an {@link Instant}, which is written as ISO 8601 with a trailing {@code Z}.</li>
 * <li>A calendar day in Sydney time (a log filter, the midnight reset) is converted to a UTC range at the
 * edge of the query, not here.</li>
 * </ul>
 */
public final class UtcTime {

	private UtcTime() {
	}

	/** The instant of a UTC wall time read from a {@code DATETIME} column. {@code null} stays {@code null}. */
	public static Instant toInstant(LocalDateTime utc) {
		return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
	}

	/** The UTC wall time to store in a {@code DATETIME(3)} column, cut to milliseconds as the column does. */
	public static LocalDateTime toLocalDateTime(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
	}

	/** The current UTC wall time to store in a {@code DATETIME(3)} column. */
	public static LocalDateTime now(Clock clock) {
		return toLocalDateTime(clock.instant());
	}

}
