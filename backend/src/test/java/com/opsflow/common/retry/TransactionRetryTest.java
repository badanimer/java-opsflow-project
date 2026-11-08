package com.opsflow.common.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

class TransactionRetryTest {

	private final List<Long> pauses = new ArrayList<>();

	private TransactionRetry retry(int attempts, Duration... delays) {
		return new TransactionRetry(new OpsflowProperties.Retry(attempts, List.of(delays)), pauses::add);
	}

	private static CannotAcquireLockException deadlock() {
		return new CannotAcquireLockException("deadlock", new SQLException("Deadlock found", "40001", 1213));
	}

	@Test
	void aCallThatSucceedsIsNotRetried() {
		AtomicInteger calls = new AtomicInteger();

		String result = retry(3, Duration.ofMillis(50)).run(() -> {
			calls.incrementAndGet();
			return "done";
		});

		assertThat(result).isEqualTo("done");
		assertThat(calls).hasValue(1);
		assertThat(pauses).isEmpty();
	}

	@Test
	void aDeadlockIsRetriedAndTheLaterTrySucceeds() {
		AtomicInteger calls = new AtomicInteger();

		String result = retry(3, Duration.ofMillis(50), Duration.ofMillis(150)).run(() -> {
			if (calls.incrementAndGet() < 3) {
				throw deadlock();
			}
			return "done";
		});

		assertThat(result).isEqualTo("done");
		assertThat(calls).hasValue(3);
	}

	@Test
	void whenEveryTryFailsTheResultIsBusyAfterExactlyTheConfiguredAttempts() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry(3, Duration.ofMillis(50), Duration.ofMillis(150)).run(() -> {
			calls.incrementAndGet();
			throw deadlock();
		})).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.code()).isEqualTo(ErrorCode.BUSY);
			assertThat(e).hasCauseInstanceOf(CannotAcquireLockException.class);
		});

		assertThat(calls).hasValue(3);
		assertThat(pauses).hasSize(2); // no pause after the last try
	}

	@Test
	void theNumberOfAttemptsComesFromTheConfiguration() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry(5, Duration.ofMillis(10)).run(() -> {
			calls.incrementAndGet();
			throw deadlock();
		})).isInstanceOf(ApiException.class);

		assertThat(calls).hasValue(5);
	}

	@Test
	void oneAttemptMeansNoRetryAtAll() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry(1, Duration.ofMillis(10)).run(() -> {
			calls.incrementAndGet();
			throw deadlock();
		})).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY));

		assertThat(calls).hasValue(1);
		assertThat(pauses).isEmpty();
	}

	@Test
	void aLockWaitTimeoutIsRetriedToo() {
		AtomicInteger calls = new AtomicInteger();

		retry(3, Duration.ofMillis(50)).run(() -> {
			if (calls.incrementAndGet() < 2) {
				throw new CannotAcquireLockException("lock wait", new SQLException("Lock wait timeout exceeded", "HY000", 1205));
			}
			return null;
		});

		assertThat(calls).hasValue(2);
	}

	@Test
	void aRawMySqlErrorBuriedInAnotherExceptionIsRecognised() {
		AtomicInteger calls = new AtomicInteger();

		retry(3, Duration.ofMillis(50)).run(() -> {
			if (calls.incrementAndGet() < 2) {
				throw new IllegalStateException("commit failed", new SQLException("Deadlock found", "40001", 1213));
			}
			return null;
		});

		assertThat(calls).hasValue(2);
	}

	@Test
	void anyOtherFailureGoesToTheCallerAtOnce() {
		AtomicInteger calls = new AtomicInteger();
		DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate key");

		assertThatThrownBy(() -> retry(3, Duration.ofMillis(50)).run(() -> {
			calls.incrementAndGet();
			throw failure;
		})).isSameAs(failure);

		assertThat(calls).hasValue(1);
	}

	@Test
	void aRefusalOfTheApiIsNeverRetried() {
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry(3, Duration.ofMillis(50)).run(() -> {
			calls.incrementAndGet();
			// A deadlock in the cause chain must not turn a business refusal into a retry
			throw new ApiException(ErrorCode.OUT_OF_STOCK, "short", deadlock());
		})).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_STOCK));

		assertThat(calls).hasValue(1);
	}

	@Test
	void theWaitGrowsWithTheConfiguredDelaysAndAddsAtMostHalfAsMuch() {
		for (int round = 0; round < 50; round++) {
			pauses.clear();
			assertThatThrownBy(() -> retry(3, Duration.ofMillis(50), Duration.ofMillis(150)).run(() -> {
				throw deadlock();
			})).isInstanceOf(ApiException.class);

			assertThat(pauses.get(0)).isBetween(50L, 75L);
			assertThat(pauses.get(1)).isBetween(150L, 225L);
		}
	}

	@Test
	void theLastDelayIsReusedWhenThereAreMoreRetriesThanDelays() {
		assertThatThrownBy(() -> retry(4, Duration.ofMillis(100)).run(() -> {
			throw deadlock();
		})).isInstanceOf(ApiException.class);

		assertThat(pauses).hasSize(3).allSatisfy(p -> assertThat(p).isBetween(100L, 150L));
	}

	@Test
	void aRunnableIsRetriedLikeASupplier() {
		AtomicInteger calls = new AtomicInteger();

		retry(3, Duration.ofMillis(50)).run((Runnable) () -> {
			if (calls.incrementAndGet() < 2) {
				throw deadlock();
			}
		});

		assertThat(calls).hasValue(2);
	}

	@Test
	void callingItInsideATransactionIsRefusedBecauseTheRetryWouldRunOnARolledBackConnection() {
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			assertThatThrownBy(() -> retry(3, Duration.ofMillis(50)).run(() -> "never"))
					.isInstanceOf(IllegalStateException.class);
		}
		finally {
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	@Test
	void anInterruptedWaitStopsTheRetryAndKeepsTheInterruptFlag() {
		TransactionRetry interrupted = new TransactionRetry(new OpsflowProperties.Retry(3, List.of(Duration.ofMillis(50))), millis -> {
			throw new InterruptedException();
		});

		try {
			assertThatThrownBy(() -> interrupted.run(() -> {
				throw deadlock();
			})).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY));
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted(); // clear the flag for the next test
		}
	}

}
