package com.opsflow.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;

/**
 * The rules of the lockout after wrong passwords (T-28, Q-07; see Login security and account recovery in
 * docs/database/database-design.md). It only calculates; {@link LoginLimits} and {@link LoginRecorder} keep
 * the counts.
 *
 * There are three counts. The failures of one account from one client address lock that address out of the
 * account in steps, so a stranger who types wrong passwords locks only their own address and the owner still
 * logs in from theirs. The failures of one account from every address lock the account itself once they reach
 * a ceiling, against guessing from many addresses. The failures of one address on any username refuse that
 * address for the rest of a window, against trying many accounts.
 *
 * The two daily counts start again at midnight in the configured time zone. A day with a change of daylight
 * saving time has 23 or 25 hours, which the time zone rules handle.
 */
@Component
public class LoginLockPolicy {

	private final Map<Integer, Duration> lockSteps;
	private final int lockUntilMidnightAt;
	private final ZoneId zone;
	private final int accountFailuresPerDay;
	private final int addressFailures;
	private final Duration addressWindow;

	@Autowired
	public LoginLockPolicy(OpsflowProperties properties) {
		this(properties.login());
	}

	LoginLockPolicy(OpsflowProperties.Login login) {
		this.lockSteps = Map.copyOf(login.lockSteps());
		this.lockUntilMidnightAt = login.lockUntilMidnightAt();
		this.zone = login.resetZone();
		this.accountFailuresPerDay = login.accountFailuresPerDay();
		this.addressFailures = login.addressFailures();
		this.addressWindow = login.addressWindow();
	}

	/** The failures of the account today, from every address, as the row counted them by {@code now}. */
	int failuresToday(int failedLoginCount, Instant lastFailedLoginAt, Instant now) {
		if (lastFailedLoginAt == null || lastFailedLoginAt.isBefore(midnight(now, 0))) {
			return 0;
		}
		return failedLoginCount;
	}

	/**
	 * Until when one address is locked out of an account after its {@code failures}-th failure there today,
	 * or empty when that failure does not lock it.
	 */
	Optional<Instant> addressLockAfter(int failures, Instant now) {
		if (failures >= lockUntilMidnightAt) {
			return Optional.of(midnight(now, 1));
		}
		return Optional.ofNullable(lockSteps.get(failures)).map(now::plus);
	}

	/**
	 * Until when the account itself is locked after its {@code failures}-th failure today from every address,
	 * or empty below the ceiling.
	 */
	Optional<Instant> accountLockAfter(int failures, Instant now) {
		return failures >= accountFailuresPerDay ? Optional.of(midnight(now, 1)) : Optional.empty();
	}

	/** The day of {@code now} in the reset zone, which names the daily count of an account at an address. */
	LocalDate day(Instant now) {
		return now.atZone(zone).toLocalDate();
	}

	/** The number of the window of the address count that {@code now} falls in. */
	long addressWindow(Instant now) {
		return now.toEpochMilli() / addressWindow.toMillis();
	}

	/** When the window of {@code now} ends. */
	Instant addressWindowEnd(Instant now) {
		return Instant.ofEpochMilli((addressWindow(now) + 1) * addressWindow.toMillis());
	}

	int addressFailures() {
		return addressFailures;
	}

	/** The start of the day of {@code now} in the reset zone, {@code daysAhead} days later. */
	private Instant midnight(Instant now, int daysAhead) {
		return now.atZone(zone).toLocalDate().plusDays(daysAhead).atStartOfDay(zone).toInstant();
	}

}
