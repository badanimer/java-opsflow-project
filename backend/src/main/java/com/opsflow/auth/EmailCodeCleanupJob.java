package com.opsflow.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;

/**
 * Deletes what is of no use any more (T-49; see Scheduled Jobs in docs/backend/backend-design.md):
 *
 * <ul>
 * <li>the email codes whose {@code expires_at} has passed, so a code is kept for 10 minutes plus at most
 * one interval, and a code that is still valid is never touched;</li>
 * <li>the request counts whose window started more than two days ago, long after any window has ended.</li>
 * </ul>
 *
 * It is safe to run twice or next to a request: it deletes by id only rows that are already dead. A run
 * deletes at most a fixed number of rows from each table and leaves the rest to the next run. Each delete
 * is a statement of its own, so the job holds no lock between them.
 */
@Component
public class EmailCodeCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(EmailCodeCleanupJob.class);
	private static final Duration COUNTS_KEPT = Duration.ofDays(2);

	private final EmailCodeRepository repository;
	private final int batch;
	private final Clock clock;

	EmailCodeCleanupJob(EmailCodeRepository repository, OpsflowProperties properties, Clock clock) {
		this.repository = repository;
		this.batch = properties.emailCode().cleanupBatch();
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${opsflow.email-code.cleanup-interval}", initialDelayString = "${opsflow.email-code.cleanup-interval}")
	public void run() {
		Instant now = clock.instant();
		int codes = repository.deleteExpiredCodes(now, batch);
		int counts = repository.deleteCountsBefore(now.minus(COUNTS_KEPT), batch);
		if (codes > 0 || counts > 0) {
			log.info("Deleted {} expired email codes and {} old request counts", codes, counts);
		}
	}

}
