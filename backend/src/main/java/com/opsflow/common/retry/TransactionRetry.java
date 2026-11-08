package com.opsflow.common.retry;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * Calls a {@code @Transactional} service method and tries again when MySQL aborted the transaction on a
 * deadlock or a lock wait timeout. See Transactions in docs/backend/backend-design.md.
 *
 * It must be called from outside any transaction. A retry inside the transaction that failed would run
 * on a connection that was already rolled back, so the call is refused with {@link IllegalStateException}
 * when a transaction is active. Every try is a new transaction: the proxy has rolled the earlier one back
 * before the exception reaches this class.
 *
 * Only a deadlock (MySQL error 1213) or a lock wait timeout (1205) is retried. Any other exception, a
 * refusal of the API included, goes to the caller at once. When the last try fails too, the result is
 * {@code BUSY}, with the last failure as its cause. A caller that must record that outcome, such as the
 * order module, catches the {@link ApiException} and checks its code.
 *
 * The wait before a retry is the configured delay plus a random extra of up to half of it, so that two
 * requests that collided do not retry at the same moment.
 */
@Component
public class TransactionRetry {

	private static final Logger log = LoggerFactory.getLogger(TransactionRetry.class);
	private static final int DEADLOCK = 1213;
	private static final int LOCK_WAIT_TIMEOUT = 1205;
	private static final String SERIALIZATION_FAILURE = "40001";

	/** Pauses the thread. Tests replace it so that they do not wait. */
	@FunctionalInterface
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	private final int attempts;
	private final List<Duration> delays;
	private final Sleeper sleeper;

	@Autowired
	public TransactionRetry(OpsflowProperties properties) {
		this(properties.retry(), Thread::sleep);
	}

	TransactionRetry(OpsflowProperties.Retry retry, Sleeper sleeper) {
		this.attempts = retry.attempts();
		this.delays = List.copyOf(retry.delays());
		this.sleeper = sleeper;
	}

	public <T> T run(Supplier<T> action) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("TransactionRetry must be called outside a transaction");
		}
		for (int attempt = 1;; attempt++) {
			try {
				return action.get();
			}
			catch (RuntimeException e) {
				if (!isRetryable(e)) {
					throw e;
				}
				if (attempt >= attempts) {
					log.warn("Giving up after {} attempts: {}", attempt, e.toString());
					throw new ApiException(ErrorCode.BUSY, "The system is busy, please try again", e);
				}
				log.info("Attempt {} lost a lock conflict, retrying: {}", attempt, e.toString());
				pause(attempt, e);
			}
		}
	}

	public void run(Runnable action) {
		run(() -> {
			action.run();
			return null;
		});
	}

	private void pause(int attempt, RuntimeException cause) {
		long base = delays.get(Math.min(attempt, delays.size()) - 1).toMillis();
		long extra = base < 2 ? 0 : ThreadLocalRandom.current().nextLong(base / 2 + 1);
		try {
			sleeper.sleep(base + extra);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ApiException(ErrorCode.BUSY, "The system is busy, please try again", cause);
		}
	}

	static boolean isRetryable(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof ApiException) {
				return false;
			}
			if (t instanceof PessimisticLockingFailureException) {
				return true;
			}
			if (t instanceof SQLException sql
					&& (sql.getErrorCode() == DEADLOCK || sql.getErrorCode() == LOCK_WAIT_TIMEOUT
							|| SERIALIZATION_FAILURE.equals(sql.getSQLState()))) {
				return true;
			}
		}
		return false;
	}

}
