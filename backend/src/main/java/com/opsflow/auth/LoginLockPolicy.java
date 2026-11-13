package com.opsflow.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;

/**
 * The rules of the lockout after wrong passwords (T-28; see Login security and account recovery in
 * docs/database/database-design.md). It only calculates; {@link LoginRecorder} reads and writes the row.
 *
 * Failures are counted in a row. The count starts again at midnight in the configured time zone, and the
 * reset is lazy: a count whose last failure was before the most recent midnight is read as zero. A day
 * with a change of daylight saving time has 23 or 25 hours, which the time zone rules handle.
 */
@Component
public class LoginLockPolicy {

	private final Map<Integer, Duration> lockSteps;
	private final int lockUntilMidnightAt;
	private final ZoneId zone;

	@Autowired
	public LoginLockPolicy(OpsflowProperties properties) {
		this(properties.login());
	}

	LoginLockPolicy(OpsflowProperties.Login login) {
		this.lockSteps = Map.copyOf(login.lockSteps());
		this.lockUntilMidnightAt = login.lockUntilMidnightAt();
		this.zone = login.resetZone();
	}

	/** The failures in a row that still count at {@code now}. */
	int failuresInARow(int failedLoginCount, Instant lastFailedLoginAt, Instant now) {
		if (lastFailedLoginAt == null || lastFailedLoginAt.isBefore(midnight(now, 0))) {
			return 0;
		}
		return failedLoginCount;
	}

	/**
	 * Until when the account is locked after its {@code failures}-th failure in a row at {@code now}, or
	 * empty when that failure does not lock it.
	 */
	Optional<Instant> lockAfter(int failures, Instant now) {
		if (failures >= lockUntilMidnightAt) {
			return Optional.of(midnight(now, 1));
		}
		return Optional.ofNullable(lockSteps.get(failures)).map(now::plus);
	}

	/** The start of the day of {@code now} in the reset zone, {@code daysAhead} days later. */
	private Instant midnight(Instant now, int daysAhead) {
		return now.atZone(zone).toLocalDate().plusDays(daysAhead).atStartOfDay(zone).toInstant();
	}

}
