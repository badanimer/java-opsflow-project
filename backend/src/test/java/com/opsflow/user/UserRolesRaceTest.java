package com.opsflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.opsflow.common.error.ApiException;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;

/**
 * The races of a change to the roles of a user (T-23, T-21; see Steps of a change to a user and the Users
 * and roles rows of Concurrency by Module in docs/database/database-design.md). Each race is run several
 * times against the MySQL container, with the real transactions and the real locks.
 */
class UserRolesRaceTest extends UserIntegrationSupport {

	private static final int ROUNDS = 10;

	/** What a racer did: {@code "OK"}, or the result code it was refused with. */
	private interface Change {
		void run();
	}

	/**
	 * Both lock the same two user rows in the same order. The second finds under the lock that it is no
	 * longer an administrator and is refused, so one administrator is always left.
	 */
	@Test
	void twoAdministratorsWhoRemoveEachOtherAtTheSameMomentLeaveOne() {
		for (int round = 0; round < ROUNDS; round++) {
			long adam = account("adam" + round, "ADMIN");
			long eve = account("eve" + round, "ADMIN");

			List<String> results = race(
					() -> users.replaceRoles(actor(adam), eve, roles(), roles("ADMIN"), null),
					() -> users.replaceRoles(actor(eve), adam, roles(), roles("ADMIN"), null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "ACCOUNT_NO_ROLE");
			assertThat(rolesOf(adam).size() + rolesOf(eve).size()).as("one of the two is still an administrator").isEqualTo(1);
			assertThat(usableAdministrators()).isEqualTo(1);
			assertThat(roleLogs(adam).size() + roleLogs(eve).size()).as("one revocation is logged").isEqualTo(1);

			// The next round starts with no administrator left over.
			root().update("DELETE FROM user_roles WHERE user_id IN (?, ?)", adam, eve);
		}
	}

	/** The two changes run one after the other, and the second sees that the roles are not what its sender saw. */
	@Test
	void ofTwoChangesToTheSameUserAtTheSameMomentOneIsRefusedAsChanged() {
		long adam = account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.replaceRoles(actor(adam), bob, roles("OPERATOR"), roles(), null),
					() -> users.replaceRoles(actor(eve), bob, roles("VIEWER"), roles(), null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "USER_CHANGED");
			assertThat(rolesOf(bob)).as("the roles of the change that won, and not a mix").hasSize(1);
			assertThat(roleLogs(bob)).hasSize(1);
		}
	}

	/** The same grant twice: one row and one log row, and no duplicate key reaches the caller. */
	@Test
	void theSameGrantSentTwiceAtTheSameMomentIsMadeOnce() {
		long adam = account("adam", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.replaceRoles(actor(adam), bob, roles("OPERATOR"), roles(), null),
					() -> users.replaceRoles(actor(adam), bob, roles("OPERATOR"), roles(), null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "USER_CHANGED");
			assertThat(rolesOf(bob)).containsExactly("OPERATOR");
			assertThat(roleLogs(bob)).hasSize(1);
		}
	}

	/**
	 * A manager gives a role while an administrator takes the manager role away. Whichever commits second
	 * sees the first: the manager either finished before the demotion, or is refused. A grant made with
	 * an authority that was already gone never happens.
	 */
	@Test
	void aManagerWhoIsDemotedWhileActingEitherFinishedFirstOrIsRefused() {
		long adam = account("adam", "ADMIN");
		int refused = 0;
		for (int round = 0; round < ROUNDS; round++) {
			long mary = account("mary" + round, "MANAGER");
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.replaceRoles(actor(adam), mary, roles(), roles("MANAGER"), null),
					() -> users.replaceRoles(actor(mary), bob, roles("OPERATOR"), roles(), null));

			assertThat(results.get(0)).as("the demotion, round %d", round).isEqualTo("OK");
			assertThat(rolesOf(mary)).isEmpty();
			if (results.get(1).equals("OK")) {
				assertThat(rolesOf(bob)).containsExactly("OPERATOR");
				assertThat(roleLogs(bob)).hasSize(1);
			}
			else {
				refused++;
				assertThat(results.get(1)).isEqualTo("ACCOUNT_NO_ROLE");
				assertThat(rolesOf(bob)).isEmpty();
				assertThat(roleLogs(bob)).isEmpty();
			}
		}
		// Not asserted: which of the two orders happens depends on the scheduler.
		System.out.println("The manager was refused in " + refused + " of " + ROUNDS + " rounds");
	}

	/**
	 * The order that the race above cannot force: the demotion holds the row of the manager first. The
	 * change of the manager waits at step 2 for that row, and when the demotion commits it reads the
	 * roles under the lock and is refused. The demotion is played by a second connection, so that the
	 * test decides when it commits.
	 */
	@Test
	void aChangeWaitsForTheRowOfItsActorAndThenSeesThatTheAuthorityIsGone() throws Exception {
		long mary = account("mary", "MANAGER");
		long bob = account("bob");
		ExecutorService thread = Executors.newSingleThreadExecutor();
		try (Connection demotion = root().getDataSource().getConnection()) {
			demotion.setAutoCommit(false);
			try (Statement statement = demotion.createStatement()) {
				statement.execute("SELECT id FROM users WHERE id = " + mary + " FOR UPDATE");

				Future<String> change = thread.submit(() -> {
					try {
						users.replaceRoles(actor(mary), bob, roles("OPERATOR"), roles(), null);
						return "OK";
					}
					catch (ApiException refusal) {
						return refusal.code().name();
					}
				});
				Thread.sleep(500);
				assertThat(change.isDone()).as("the change waits for the row of its actor").isFalse();

				statement.execute("DELETE FROM user_roles WHERE user_id = " + mary);
				demotion.commit();

				assertThat(change.get(30, TimeUnit.SECONDS)).isEqualTo("ACCOUNT_NO_ROLE");
			}
		}
		finally {
			thread.shutdownNow();
		}
		assertThat(rolesOf(bob)).isEmpty();
		assertThat(roleLogs(bob)).isEmpty();
	}

	/** Runs the changes at the same moment and returns what each did, in the order they were given. */
	private static List<String> race(Change... changes) {
		AtomicInteger next = new AtomicInteger();
		String[] results = new String[changes.length];
		List<Outcome<Integer>> outcomes = RaceRunner.run(changes.length, () -> {
			int index = next.getAndIncrement();
			try {
				changes[index].run();
				results[index] = "OK";
			}
			catch (ApiException refusal) {
				results[index] = refusal.code().name();
			}
			return index;
		});
		assertThat(outcomes).as("no racer ended with an unexpected exception").allMatch(Outcome::succeeded);
		return List.of(results);
	}

}
