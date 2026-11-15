package com.opsflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * Replacing the roles of a user (T-21, T-15, T-26, T-23, T-06; see Role management, Authority over roles
 * and accounts, and Steps of a change to a user in docs/database/database-design.md). Through HTTP calls
 * against the MySQL container, and through the service where a test needs a request that arrived before
 * the actor was changed.
 */
class UserRolesTest extends UserIntegrationSupport {

	@Autowired
	UserRepository repository;

	@Test
	void anAdministratorGivesAndTakesAwayAnyRoleAndEachChangeIsLogged() throws Exception {
		long adam = account("adam", "ADMIN");
		long bob = account("bob");
		String token = tokenOf("adam");

		replaceRoles(token, bob, roles("OPERATOR", "MANAGER"), roles())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(bob))
				.andExpect(jsonPath("$.username").value("bob"))
				.andExpect(jsonPath("$.roles", contains("MANAGER", "OPERATOR")));

		assertThat(rolesOf(bob)).containsExactly("MANAGER", "OPERATOR");
		List<Map<String, Object>> logs = roleLogs(bob);
		assertThat(logs).hasSize(2);
		assertThat(logs).allSatisfy(log -> {
			assertThat(log.get("action")).isEqualTo("ROLE_GRANTED");
			assertThat(log.get("category")).isEqualTo("ROLE");
			assertThat(log.get("operator_id")).isEqualTo(adam);
			assertThat(log.get("target_type")).isEqualTo("USER");
			assertThat(log.get("before_data")).isNull();
			assertThat(log.get("ip_address")).isEqualTo("127.0.0.1");
			assertThat(log.get("remark")).isEqualTo("replace roles");
		});
		assertThat(json(logs.get(0), "after_data")).containsEntry("roleCode", "MANAGER").containsEntry("username", "bob")
				.containsEntry("roleId", roleId("MANAGER"));
		assertThat(json(logs.get(1), "after_data")).containsEntry("roleCode", "OPERATOR").containsEntry("username", "bob");
	}

	@Test
	void aRoleThatStaysIsLeftAloneAndOnlyTheDifferenceIsLogged() throws Exception {
		long adam = account("adam", "ADMIN");
		long bob = account("bob", "MANAGER", "OPERATOR");
		long operatorGrant = grantId(bob, "OPERATOR");

		replaceRoles(tokenOf("adam"), bob, roles("OPERATOR", "VIEWER"), roles("MANAGER", "OPERATOR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", contains("OPERATOR", "VIEWER")));

		assertThat(rolesOf(bob)).containsExactly("OPERATOR", "VIEWER");
		assertThat(grantId(bob, "OPERATOR")).as("the grant that stays is the same row").isEqualTo(operatorGrant);
		List<Map<String, Object>> logs = roleLogs(bob);
		assertThat(logs).extracting(log -> log.get("action")).containsExactly("ROLE_REVOKED", "ROLE_GRANTED");
		assertThat(json(logs.get(0), "before_data")).containsEntry("roleCode", "MANAGER").containsEntry("username", "bob")
				.containsEntry("roleId", roleId("MANAGER"));
		assertThat(logs.get(0).get("after_data")).isNull();
		assertThat(logs.get(0).get("operator_id")).isEqualTo(adam);
		assertThat(json(logs.get(1), "after_data")).containsEntry("roleCode", "VIEWER");
	}

	@Test
	void aListEqualToTheRolesOfTheUserChangesNothingAndWritesNoLog() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR");
		long grant = grantId(bob, "OPERATOR");

		replaceRoles(tokenOf("adam"), bob, roles("OPERATOR"), roles("OPERATOR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", contains("OPERATOR")));

		assertThat(grantId(bob, "OPERATOR")).isEqualTo(grant);
		assertThat(roleLogs(bob)).isEmpty();
	}

	@Test
	void anEmptyListLeavesTheUserWithNoRole() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR", "VIEWER");

		replaceRoles(tokenOf("adam"), bob, roles(), roles("VIEWER", "OPERATOR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", hasSize(0)));

		assertThat(rolesOf(bob)).isEmpty();
		assertThat(roleLogs(bob)).extracting(log -> log.get("action")).containsExactly("ROLE_REVOKED", "ROLE_REVOKED");
	}

	@Test
	void aRoleNamedTwiceIsGrantedOnce() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob");

		replaceRoles(tokenOf("adam"), bob, roles("VIEWER", "VIEWER"), roles()).andExpect(status().isOk());

		assertThat(rolesOf(bob)).containsExactly("VIEWER");
		assertThat(roleLogs(bob)).hasSize(1);
	}

	@Test
	void aManagerGivesAndTakesAwayTheRolesBelowManager() throws Exception {
		long mary = account("mary", "MANAGER");
		long bob = account("bob", "VIEWER");

		replaceRoles(tokenOf("mary"), bob, roles("OPERATOR"), roles("VIEWER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", contains("OPERATOR")));

		assertThat(rolesOf(bob)).containsExactly("OPERATOR");
		assertThat(roleLogs(bob)).allSatisfy(log -> assertThat(log.get("operator_id")).isEqualTo(mary));
	}

	@Test
	void aManagerCannotGiveAProtectedRole() throws Exception {
		account("mary", "MANAGER");
		long bob = account("bob", "OPERATOR");
		String token = tokenOf("mary");

		forbidden(replaceRoles(token, bob, roles("OPERATOR", "MANAGER"), roles("OPERATOR")));
		forbidden(replaceRoles(token, bob, roles("ADMIN"), roles("OPERATOR")));

		assertThat(rolesOf(bob)).containsExactly("OPERATOR");
		assertThat(roleLogs(bob)).isEmpty();
	}

	@Test
	void aManagerCannotChangeAUserWhoHoldsAProtectedRole() throws Exception {
		account("mary", "MANAGER");
		long max = account("max", "MANAGER", "OPERATOR");
		long adam = account("adam", "ADMIN");
		String token = tokenOf("mary");

		forbidden(replaceRoles(token, max, roles("MANAGER"), roles("MANAGER", "OPERATOR")));
		forbidden(replaceRoles(token, max, roles(), roles("MANAGER", "OPERATOR")));
		forbidden(replaceRoles(token, adam, roles("ADMIN", "VIEWER"), roles("ADMIN")));

		assertThat(rolesOf(max)).containsExactly("MANAGER", "OPERATOR");
		assertThat(rolesOf(adam)).containsExactly("ADMIN");
	}

	@Test
	void nobodyChangesTheirOwnRoles() throws Exception {
		long adam = account("adam", "ADMIN");
		long mary = account("mary", "MANAGER");

		forbidden(replaceRoles(tokenOf("adam"), adam, roles("ADMIN", "OPERATOR"), roles("ADMIN")))
				.andExpect(jsonPath("$.detail").value("Nobody changes their own account. Another administrator must do it"));
		forbidden(replaceRoles(tokenOf("mary"), mary, roles(), roles("MANAGER")));

		assertThat(rolesOf(adam)).containsExactly("ADMIN");
		assertThat(rolesOf(mary)).containsExactly("MANAGER");
	}

	@Test
	void anOperatorAViewerAndAUserWithNoRoleCannotCallTheEndpoint() throws Exception {
		account("oscar", "OPERATOR");
		account("vera", "VIEWER");
		account("nora");
		long bob = account("bob");

		forbidden(replaceRoles(tokenOf("oscar"), bob, roles("VIEWER"), roles()));
		forbidden(replaceRoles(tokenOf("vera"), bob, roles("VIEWER"), roles()));
		replaceRoles(tokenOf("nora"), bob, roles("VIEWER"), roles())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));
		mvc.perform(put("/api/v1/users/" + bob + "/roles").contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[],\"expectedRoles\":[]}"))
				.andExpect(status().isUnauthorized());

		assertThat(rolesOf(bob)).isEmpty();
	}

	@Test
	void theChangeIsRefusedWhenTheRolesAreNoLongerWhatTheActorSaw() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob", "VIEWER");

		replaceRoles(tokenOf("adam"), bob, roles("OPERATOR"), roles())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER_CHANGED"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
		replaceRoles(tokenOf("adam"), bob, roles("OPERATOR"), roles("VIEWER", "OPERATOR"))
				.andExpect(jsonPath("$.code").value("USER_CHANGED"));

		assertThat(rolesOf(bob)).containsExactly("VIEWER");
		assertThat(roleLogs(bob)).isEmpty();
	}

	/** Revoking is always allowed, and a grant needs a usable account (see Role grants). */
	@Test
	void aDisabledUserCanLoseRolesAndCannotBeGivenOne() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR", "VIEWER");
		disable(bob);
		String token = tokenOf("adam");

		replaceRoles(token, bob, roles("OPERATOR"), roles("OPERATOR", "VIEWER")).andExpect(status().isOk());
		replaceRoles(token, bob, roles("OPERATOR", "MANAGER"), roles("OPERATOR"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));
		replaceRoles(token, bob, roles("VIEWER"), roles("OPERATOR"))
				.andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));

		assertThat(rolesOf(bob)).containsExactly("OPERATOR");
		assertThat(roleLogs(bob)).extracting(log -> log.get("action")).containsExactly("ROLE_REVOKED");
	}

	@Test
	void aUserWhoDoesNotExistWasDeletedOrIsTheSystemIdentityIsNotFound() throws Exception {
		account("adam", "ADMIN");
		long gone = account("gone", "OPERATOR");
		softDelete(gone);
		String token = tokenOf("adam");

		for (long id : new long[] { 999_999, gone, systemIdentity() }) {
			replaceRoles(token, id, roles("VIEWER"), roles())
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

		assertThat(rolesOf(gone)).containsExactly("OPERATOR");
		assertThat(rolesOf(systemIdentity())).isEmpty();
	}

	@Test
	void aRequestThatIsNotWellFormedIsAValidationError() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob");
		String token = tokenOf("adam");

		replaceRoles(token, bob, roles("AUDITOR"), roles())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("roles"));
		replaceRoles(token, bob, roles("operator"), roles()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		send(token, bob, "{\"roles\":[\"VIEWER\"]}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		send(token, bob, "{\"expectedRoles\":[]}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		send(token, bob, "{\"roles\":[\"\"],\"expectedRoles\":[]}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(rolesOf(bob)).isEmpty();
	}

	@Test
	void anAdministratorTakesTheRoleFromAnotherAdministrator() throws Exception {
		account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");

		replaceRoles(tokenOf("adam"), eve, roles("MANAGER"), roles("ADMIN")).andExpect(status().isOk());

		assertThat(rolesOf(eve)).containsExactly("MANAGER");
		assertThat(usableAdministrators()).isEqualTo(1);
	}

	/**
	 * Step 3: the roles are read again under the lock. A request that passed the check of the endpoint
	 * while its sender was an administrator finds here what the sender holds now.
	 */
	@Test
	void anActorWhoLostTheAuthorityAfterTheRequestArrivedIsRefused() {
		long mary = account("mary", "MANAGER");
		long nora = account("nora");
		long dan = account("dan", "ADMIN");
		disable(dan);
		long gone = account("gone", "ADMIN");
		softDelete(gone);
		long bob = account("bob");

		assertRefused(ErrorCode.FORBIDDEN, () -> users.replaceRoles(actor(mary), bob, roles("MANAGER"), roles(), null));
		assertRefused(ErrorCode.ACCOUNT_NO_ROLE, () -> users.replaceRoles(actor(nora), bob, roles("VIEWER"), roles(), null));
		assertRefused(ErrorCode.ACCOUNT_DISABLED, () -> users.replaceRoles(actor(dan), bob, roles("VIEWER"), roles(), null));
		assertRefused(ErrorCode.UNAUTHENTICATED, () -> users.replaceRoles(actor(gone), bob, roles("VIEWER"), roles(), null));

		assertThat(rolesOf(bob)).isEmpty();
		assertThat(roleLogs(bob)).isEmpty();
	}

	/** The count behind Administrator protection: only a usable account counts. */
	@Test
	void onlyUsableAccountsCountAsHoldersOfARole() {
		account("adam", "ADMIN");
		disable(account("dan", "ADMIN"));
		softDelete(account("gone", "ADMIN"));
		account("mary", "MANAGER");

		assertThat(repository.countUsableHolders("ADMIN")).isEqualTo(1);
		assertThat(repository.countUsableHolders("MANAGER")).isEqualTo(1);
		assertThat(repository.countUsableHolders("VIEWER")).isZero();
	}

	private static ResultActions forbidden(ResultActions answer) throws Exception {
		return answer.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private ResultActions send(String token, long userId, String body) throws Exception {
		return mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static void assertRefused(ErrorCode code, Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
	}

	private long grantId(long userId, String roleCode) {
		return root().queryForObject(
				"SELECT ur.id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? AND r.code = ?", Long.class,
				userId, roleCode);
	}

	private int roleId(String code) {
		return root().queryForObject("SELECT id FROM roles WHERE code = ?", Integer.class, code);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> json(Map<String, Object> log, String column) {
		return com.jayway.jsonpath.JsonPath.parse((String) log.get(column)).read("$", Map.class);
	}

}
