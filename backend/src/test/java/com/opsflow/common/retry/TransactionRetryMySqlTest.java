package com.opsflow.common.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.support.MySqlIntegrationTest;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;

/**
 * {@link TransactionRetry} against a real MySQL: a real deadlock, and a real lock wait timeout. The
 * transactions run through Spring's transaction manager like the {@code @Transactional} methods of the
 * services will, and the retry sits outside them. The rows are two roles, which the application account
 * may update.
 */
class TransactionRetryMySqlTest extends MySqlIntegrationTest {

	@Autowired
	TransactionRetry retry;

	@Autowired
	PlatformTransactionManager transactions;

	private long roleA;
	private long roleB;

	@BeforeEach
	void twoRoles() {
		root().update("INSERT INTO roles (code, name) VALUES ('A', 'Role A'), ('B', 'Role B')");
		roleA = root().queryForObject("SELECT id FROM roles WHERE code = 'A'", Long.class);
		roleB = root().queryForObject("SELECT id FROM roles WHERE code = 'B'", Long.class);
	}

	private void touch(long id, String text) {
		jdbc.update("UPDATE roles SET description = ? WHERE id = ?", text, id);
	}

	@Test
	void twoTransactionsLockingTheSameRowsInOppositeOrderBothSucceedAfterTheLoserRetries() {
		CyclicBarrier bothHoldTheirFirstRow = new CyclicBarrier(2);
		AtomicInteger threadNumber = new AtomicInteger();
		AtomicInteger attempts = new AtomicInteger();

		List<Outcome<String>> outcomes = RaceRunner.run(2, () -> {
			int me = threadNumber.getAndIncrement();
			long first = me == 0 ? roleA : roleB;
			long second = me == 0 ? roleB : roleA;
			AtomicInteger myAttempts = new AtomicInteger();
			return retry.run(() -> new TransactionTemplate(transactions).execute(status -> {
				attempts.incrementAndGet();
				touch(first, "thread " + me);
				if (myAttempts.incrementAndGet() == 1) {
					await(bothHoldTheirFirstRow); // make the deadlock certain: each holds one row and wants the other
				}
				touch(second, "thread " + me);
				return "thread " + me;
			}));
		});

		assertThat(outcomes).allSatisfy(o -> assertThat(o.succeeded()).as("outcome %s", o).isTrue());
		assertThat(attempts.get()).as("MySQL aborted exactly one of the two, which tried again").isEqualTo(3);
		// The winner committed first and the loser's retry wrote last, so both rows hold the loser's text.
		List<String> texts = root().queryForList("SELECT description FROM roles ORDER BY id", String.class);
		assertThat(texts).hasSize(2).doesNotContainNull();
		assertThat(texts.get(0)).isEqualTo(texts.get(1));
	}

	@Test
	void aLockWaitThatNeverEndsIsRetriedTheConfiguredNumberOfTimesAndThenAnswersBusy() throws SQLException {
		AtomicInteger attempts = new AtomicInteger();

		try (Connection holder = root().getDataSource().getConnection()) {
			holder.setAutoCommit(false);
			holder.createStatement().executeQuery("SELECT id FROM roles WHERE id = " + roleA + " FOR UPDATE").close();

			Throwable failure = null;
			try {
				retry.run(() -> new TransactionTemplate(transactions).execute(status -> {
					attempts.incrementAndGet();
					jdbc.execute("SET SESSION innodb_lock_wait_timeout = 1");
					try {
						touch(roleA, "never written");
					}
					finally {
						jdbc.execute("SET SESSION innodb_lock_wait_timeout = DEFAULT");
					}
					return null;
				}));
			}
			catch (Throwable e) {
				failure = e;
			}
			finally {
				holder.rollback();
			}

			assertThat(failure).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.BUSY));
		}

		assertThat(attempts).as("the configured attempts, no more").hasValue(3);
		assertThat(root().queryForObject("SELECT description FROM roles WHERE id = ?", String.class, roleA)).isNull();
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(30, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException("The two transactions did not meet", e);
		}
	}

}
