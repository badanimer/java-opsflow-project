package com.opsflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opsflow.support.MySqlIntegrationTest;

/**
 * The roles in the code are the roles in the database. The four roles are fixed (see Role management in
 * docs/database/database-design.md), so a role added to one side only fails here.
 */
class RoleTest extends MySqlIntegrationTest {

	@Test
	void theRolesOfTheCodeAreTheRowsOfTheSeedMigration() {
		List<String> inTheDatabase = root().queryForList("SELECT code FROM roles", String.class);

		assertThat(Arrays.stream(Role.values()).map(Role::code)).containsExactlyInAnyOrderElementsOf(inTheDatabase);
	}

	@Test
	void aRoleIsFoundByItsCode() {
		for (Role role : Role.values()) {
			assertThat(Role.ofCode(role.code())).contains(role);
		}
		assertThat(Role.ofCode("AUDITOR")).isEmpty();
		assertThat(Role.ofCode("admin")).isEmpty();
	}

	@Test
	void aUserHoldsTheRolesOfTheirCodes() {
		CurrentUser user = new CurrentUser(7, "olivia", List.of("OPERATOR", "VIEWER"));

		assertThat(user.holds(Role.OPERATOR)).isTrue();
		assertThat(user.holds(Role.VIEWER)).isTrue();
		assertThat(user.holds(Role.MANAGER)).isFalse();
		assertThat(new CurrentUser(8, "alice", List.of()).holds(Role.VIEWER)).isFalse();
	}

}
