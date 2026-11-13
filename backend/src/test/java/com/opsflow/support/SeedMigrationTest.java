package com.opsflow.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The rows of the seed migration V2 (T-05): the four roles and the SYSTEM identity. Flyway applied the
 * migration when the container was set up, and the base class puts the same rows back before each test.
 */
class SeedMigrationTest extends MySqlIntegrationTest {

	@Test
	void flywayAppliedTheSeedMigration() {
		List<String> versions = root().queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
				String.class);

		// Later migrations follow it.
		assertThat(versions).startsWith("1", "2");
	}

	@Test
	void theFourRolesAreSeeded() {
		List<String> codes = jdbc.queryForList("SELECT code FROM roles ORDER BY id", String.class);

		assertThat(codes).containsExactly("ADMIN", "MANAGER", "OPERATOR", "VIEWER");
	}

	@Test
	void theSystemIdentityIsOneRowThatIsNotAPersonAndHoldsNoRole() {
		Map<String, Object> system = jdbc.queryForMap(
				"SELECT id, username, email, password_hash, status, deleted FROM users WHERE is_system");

		assertThat(system).containsEntry("username", "system").containsEntry("email", "system@opsflow.invalid")
				.containsEntry("status", "ACTIVE").containsEntry("deleted", false);
		// A hash of the application starts with its algorithm in braces. The marker does not, so nothing matches it.
		assertThat((String) system.get("password_hash")).doesNotStartWith("{").doesNotStartWith("$2");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles", Integer.class)).isZero();
	}

}
