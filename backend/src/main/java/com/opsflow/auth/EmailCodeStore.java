package com.opsflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * The transactions that write an email code (see Email codes in docs/database/database-design.md).
 *
 * {@link #store} is the whole of a request for a code: it counts the request against the two limits,
 * checks the 60-second rule, marks the earlier codes used, and stores the new one. A refusal is thrown,
 * so the transaction rolls back and a refused request is not counted. The count of the whole system is
 * one row that every request updates, so requests wait for each other there, and the checks behind it
 * cannot be passed by two requests at the same moment. Lock order: the count of the client address,
 * the count of the system, then the code rows. No other table is locked.
 *
 * The mail is not sent here: {@link EmailCodeService} sends it after this transaction has committed.
 */
@Component
public class EmailCodeStore {

	static final String ALL = "ALL";
	static final String IP_PREFIX = "IP:";

	private final EmailCodeRepository repository;
	private final OpsflowProperties.EmailCode settings;
	private final Clock clock;

	EmailCodeStore(EmailCodeRepository repository, OpsflowProperties properties, Clock clock) {
		this.repository = repository;
		this.settings = properties.emailCode();
		this.clock = clock;
	}

	@Transactional
	public void store(String email, EmailCodePurpose purpose, String codeHash, String ipAddress) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (repository.countRequest(IP_PREFIX + ipAddress, now.truncatedTo(ChronoUnit.HOURS)) > settings.requestsPerIpPerHour()) {
			throw tooManyRequests();
		}
		if (repository.countRequest(ALL, startOfDay(now)) > settings.requestsPerDay()) {
			throw tooManyRequests();
		}
		if (repository.requestedAfter(email, purpose, now.minus(settings.resendAfter()))) {
			throw tooManyRequests();
		}
		repository.consumeAll(email, purpose, now);
		repository.insert(email, purpose, codeHash, now, now.plus(settings.ttl()));
	}

	/** A wrong entry stays counted whatever the caller answers, so it has a transaction of its own. */
	@Transactional
	public void recordWrongEntry(long codeId) {
		repository.addWrongEntry(codeId);
	}

	/** Midnight in the configured zone, as an instant. A day there may have 23 or 25 hours. */
	Instant startOfDay(Instant now) {
		return now.atZone(settings.dayZone()).toLocalDate().atStartOfDay(settings.dayZone()).toInstant();
	}

	/** One answer for the three limits, so it does not show which one was reached. */
	private static ApiException tooManyRequests() {
		return new ApiException(ErrorCode.TOO_MANY_REQUESTS, "Too many code requests. Try again later.");
	}

}
