package com.opsflow.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

/** Checks the test setup itself: the schema, the limited account, the clean start, and the race runner. */
class TestInfrastructureTest extends MySqlIntegrationTest {

	private static final String INSERT_ROLE = "INSERT INTO roles (code, name) VALUES (?, ?)";

	@Test
	void migrationsBuildTheSixteenTables() {
		Integer tables = jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'opsflow' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'",
				Integer.class);
		assertThat(tables).isEqualTo(16);
	}

	@Test
	void sessionsRunInUtc() {
		String zone = jdbc.queryForObject("SELECT @@session.time_zone", String.class);
		assertThat(zone).isIn("UTC", "+00:00");
	}

	@Test
	void applicationAccountHasOnlyItsPerTableRights() {
		jdbc.update("INSERT INTO operation_logs (category, action, target_type, target_id) VALUES ('ACCOUNT', 'TEST', 'users', 1)");

		// The refusal is in the root cause: Spring wraps the driver's message.
		// An append-only table accepts INSERT and nothing else.
		assertThatThrownBy(() -> jdbc.update("UPDATE operation_logs SET action = 'CHANGED'"))
			.isInstanceOf(DataAccessException.class)
			.rootCause().hasMessageContaining("denied");
		// DELETE is granted on one table only, email_verification_codes.
		assertThatThrownBy(() -> jdbc.update("DELETE FROM operation_logs"))
			.isInstanceOf(DataAccessException.class)
			.rootCause().hasMessageContaining("denied");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM roles"))
			.isInstanceOf(DataAccessException.class)
			.rootCause().hasMessageContaining("denied");
	}

	@Test
	void everyTestStartsWithEmptyTables() {
		// The next test, whichever runs first, would see this row if the tables were not emptied.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isZero();
		jdbc.update(INSERT_ROLE, "LEFTOVER", "Leftover");
	}

	@Test
	void everyTestStartsWithEmptyTablesToo() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isZero();
		jdbc.update(INSERT_ROLE, "LEFTOVER", "Leftover");
	}

	@Test
	void ofEightRacersOnAUniqueKeyExactlyOneWins() {
		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(8, () -> jdbc.update(INSERT_ROLE, "RACE", "Race"));

		assertThat(outcomes).filteredOn(RaceRunner.Outcome::succeeded).hasSize(1);
		assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
			.allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(DuplicateKeyException.class));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(1);
	}

}
