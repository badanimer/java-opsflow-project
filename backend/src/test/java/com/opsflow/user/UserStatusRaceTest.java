package com.opsflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.opsflow.auth.AccountWriter;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.time.UtcTime;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;

/**
 * The races of disabling and soft-deleting a user (T-21, T-23; see Steps of a change to a user and the
 * Users and roles and Authentication rows of Concurrency by Module in docs/database/database-design.md).
 * Each race is run several times against the MySQL container, with the real transactions and locks.
 *
 * In several of them either order is right, and which one happens depends on the scheduler. The test
 * then checks that the result is one of the two, and that what was written agrees with it.
 */
class UserStatusRaceTest extends UserIntegrationSupport {

	private static final int ROUNDS = 10;

	@Autowired
	AccountWriter accountWriter;

	private interface Change {
		void run();
	}

	/** Both lock the same two rows in the same order. The second finds itself disabled and is refused. */
	@Test
	void twoAdministratorsWhoDisableEachOtherAtTheSameMomentLeaveOne() {
		for (int round = 0; round < ROUNDS; round++) {
			long adam = account("adam" + round, "ADMIN");
			long eve = account("eve" + round, "ADMIN");

			List<String> results = race(
					() -> users.disable(actor(adam), eve, null),
					() -> users.disable(actor(eve), adam, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "ACCOUNT_DISABLED");
			assertThat(usableAdministrators()).as("one of the two is still a usable administrator").isEqualTo(1);
			assertThat(accountLogs(adam).size() + accountLogs(eve).size()).as("one disabling is logged").isEqualTo(1);

			// The next round starts with no administrator left over.
			root().update("DELETE FROM user_roles WHERE user_id IN (?, ?)", adam, eve);
		}
	}

	@Test
	void ofTwoDisablingsOfTheSameUserAtTheSameMomentOneIsRefusedAsChanged() {
		long adam = account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long bob = account("bob" + round, "OPERATOR");

			List<String> results = race(
					() -> users.disable(actor(adam), bob, null),
					() -> users.disable(actor(eve), bob, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "USER_CHANGED");
			assertThat(statusOf(bob)).isEqualTo("DISABLED");
			assertThat(accountLogs(bob)).hasSize(1);
		}
	}

	/**
	 * A role is given while the user is disabled. If the grant commits first, the user is disabled with
	 * the role. If the disabling commits first, the grant is refused, because a disabled user cannot be
	 * given a role. A role granted to a user who was already disabled never happens.
	 */
	@Test
	void aReplacementOfTheRolesAndADisablingOfTheSameUserRunOneAfterTheOther() {
		long adam = account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.replaceRoles(actor(adam), bob, roles("OPERATOR"), roles(), null),
					() -> users.disable(actor(eve), bob, null));

			assertThat(results.get(1)).as("the disabling, round %d", round).isEqualTo("OK");
			assertThat(statusOf(bob)).isEqualTo("DISABLED");
			if (results.get(0).equals("OK")) {
				assertThat(rolesOf(bob)).containsExactly("OPERATOR");
				assertThat(roleLogs(bob)).hasSize(1);
			}
			else {
				assertThat(results.get(0)).isEqualTo("USER_NOT_ACTIVE");
				assertThat(rolesOf(bob)).isEmpty();
				assertThat(roleLogs(bob)).isEmpty();
			}
		}
	}

	/** The deletion always happens. The disabling either came first and is logged, or finds no user. */
	@Test
	void aDisablingAndADeletionOfTheSameUserRunOneAfterTheOther() {
		long adam = account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.disable(actor(adam), bob, null),
					() -> users.softDelete(actor(eve), bob, null));

			assertThat(results.get(1)).as("the deletion, round %d", round).isEqualTo("OK");
			assertThat(isDeleted(bob)).isTrue();
			assertThat(statusOf(bob)).isEqualTo("DISABLED");
			List<String> actions = accountLogs(bob).stream().map(log -> (String) log.get("action")).toList();
			if (results.get(0).equals("OK")) {
				assertThat(actions).containsExactly("USER_STATUS_CHANGED", "USER_DELETED");
				assertThat(data(accountLogs(bob).get(1), "before_data")).as("the deletion saw the disabling")
						.containsEntry("status", "DISABLED");
			}
			else {
				assertThat(results.get(0)).isEqualTo("NOT_FOUND");
				assertThat(actions).containsExactly("USER_DELETED");
			}
		}
	}

	/** A manager disables a user while being disabled: the manager either finished first or is refused. */
	@Test
	void anActorWhoIsDisabledWhileActingEitherFinishedFirstOrIsRefused() {
		long adam = account("adam", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			long mary = account("mary" + round, "MANAGER");
			long bob = account("bob" + round);

			List<String> results = race(
					() -> users.disable(actor(adam), mary, null),
					() -> users.disable(actor(mary), bob, null));

			assertThat(results.get(0)).as("the disabling of the manager, round %d", round).isEqualTo("OK");
			assertThat(statusOf(mary)).isEqualTo("DISABLED");
			if (results.get(1).equals("OK")) {
				assertThat(statusOf(bob)).isEqualTo("DISABLED");
				assertThat(accountLogs(bob)).hasSize(1);
			}
			else {
				assertThat(results.get(1)).isEqualTo("ACCOUNT_DISABLED");
				assertThat(statusOf(bob)).isEqualTo("ACTIVE");
				assertThat(accountLogs(bob)).isEmpty();
			}
		}
	}

	/**
	 * The password reset and the deletion need the same user row. A reset that commits first is kept and
	 * logged; one that waits finds the account deleted and writes nothing (see the Authentication rows of
	 * Concurrency by Module). The reset is the real transaction of the auth module, with a code row made here.
	 */
	@Test
	void aPasswordResetAndADeletionOfTheSameUserRunOneAfterTheOther() {
		long adam = account("adam", "ADMIN");
		for (int round = 0; round < ROUNDS; round++) {
			String name = "bob" + round;
			long bob = account(name);
			String oldHash = root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, bob);
			String newHash = "{bcrypt}made-by-the-test-" + round;
			root().update(
					"INSERT INTO email_verification_codes (email, purpose, code_hash, expires_at, created_at) VALUES (?, 'RESET_PASSWORD', 'made-by-the-test', ?, ?)",
					name + "@example.com", UtcTime.now(clock).plusMinutes(10), UtcTime.now(clock));
			long codeId = root().queryForObject("SELECT MAX(id) FROM email_verification_codes", Long.class);

			boolean[] reset = new boolean[1];
			List<String> results = race(
					() -> reset[0] = accountWriter.resetPassword(codeId, bob, oldHash, newHash, null),
					() -> users.softDelete(actor(adam), bob, null));

			assertThat(results).as("round %d", round).containsExactly("OK", "OK");
			assertThat(isDeleted(bob)).isTrue();
			String hash = root().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, bob);
			List<String> actions = accountLogs(bob).stream().map(log -> (String) log.get("action")).toList();
			if (reset[0]) {
				assertThat(hash).isEqualTo(newHash);
				assertThat(actions).containsExactly("PASSWORD_RESET", "USER_DELETED");
			}
			else {
				assertThat(hash).as("a reset that found the account deleted changed nothing").isEqualTo(oldHash);
				assertThat(actions).containsExactly("USER_DELETED");
			}
		}
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
