package com.opsflow.ticket;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;

/**
 * Archives resolved tickets by itself (T-33; see Archive in docs/database/database-design.md and Scheduled
 * Jobs in docs/backend/backend-design.md):
 *
 * <ul>
 * <li>every ticket resolved at least the configured delay ago (7 days);</li>
 * <li>all {@code RESOLVED} tickets when the system holds the configured number of them (99).</li>
 * </ul>
 *
 * Each ticket is archived in its own transaction through the status gate, as the SYSTEM identity, so a
 * ticket that someone archived or changed in the meantime is skipped and the rest go on. A run archives
 * at most the configured batch, and the next run takes the rest. The count is read before the run and
 * need not be exact. Running the job twice, or next to a person's archive, is safe.
 */
@Component
public class TicketArchiveJob {

	static final String AFTER_DELAY = "auto archive after 7 days";
	static final String AT_COUNT = "auto archive at 99 resolved";

	private static final Logger log = LoggerFactory.getLogger(TicketArchiveJob.class);

	private final TicketRepository repository;
	private final TicketWriter writer;
	private final TransactionRetry retry;
	private final Clock clock;
	private final Duration delay;
	private final int atCount;
	private final int batch;

	TicketArchiveJob(TicketRepository repository, TicketWriter writer, TransactionRetry retry, Clock clock, OpsflowProperties properties) {
		this.repository = repository;
		this.writer = writer;
		this.retry = retry;
		this.clock = clock;
		this.delay = properties.ticket().autoArchiveAfter();
		this.atCount = properties.ticket().autoArchiveAtResolved();
		this.batch = properties.ticket().archiveJobBatch();
	}

	@Scheduled(fixedDelayString = "${opsflow.ticket.archive-interval}", initialDelayString = "${opsflow.ticket.archive-interval}")
	public void run() {
		String runId = UUID.randomUUID().toString();
		int byCount = 0;
		int byDelay;
		if (repository.countResolved() >= atCount) {
			byCount = archive(repository.resolved(batch), runId, AT_COUNT);
		}
		byDelay = archive(repository.resolvedBefore(clock.instant().minus(delay), batch), runId, AFTER_DELAY);
		if (byCount > 0 || byDelay > 0) {
			log.info("Archived {} resolved tickets at the count and {} after the delay", byCount, byDelay);
		}
	}

	private int archive(List<Long> ids, String runId, String remark) {
		int archived = 0;
		for (long id : ids) {
			try {
				retry.run(() -> writer.archive(null, id, runId, remark, null));
				archived++;
			}
			catch (ApiException skipped) {
				// Archived or changed by someone else meanwhile, or the retries were used up: the next run looks again.
			}
		}
		return archived;
	}

}
