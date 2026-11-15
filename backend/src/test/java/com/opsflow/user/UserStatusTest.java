package com.opsflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * Disabling, enabling, and soft-deleting a user (T-21, T-19, T-23, T-26, T-36, T-50; see Steps of a
 * change to a user, Authority over roles and accounts, and User and account logs in
 * docs/database/database-design.md). Through HTTP calls against the MySQL container, and through the
 * service where a test needs a request that arrived before the actor was changed.
 */
class UserStatusTest extends UserIntegrationSupport {

	@Test
	void anAdministratorDisablesAUserWhoKeepsTheRolesAndIsRefusedOnTheNextRequest() throws Exception {
		long adam = account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR", "VIEWER");
		String tokenOfBob = tokenOf("bob");
		me(tokenOfBob).andExpect(status().isOk());

		disableUser(tokenOf("adam"), bob)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(bob))
				.andExpect(jsonPath("$.username").value("bob"))
				.andExpect(jsonPath("$.status").value("DISABLED"));

		assertThat(statusOf(bob)).isEqualTo("DISABLED");
		assertThat(isDeleted(bob)).isFalse();
		assertThat(rolesOf(bob)).as("the roles stay").containsExactly("OPERATOR", "VIEWER");
		me(tokenOfBob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

		List<Map<String, Object>> logs = accountLogs(bob);
		assertThat(logs).hasSize(1);
		Map<String, Object> log = logs.get(0);
		assertThat(log.get("action")).isEqualTo("USER_STATUS_CHANGED");
		assertThat(log.get("category")).isEqualTo("ACCOUNT");
		assertThat(log.get("operator_id")).isEqualTo(adam);
		assertThat(log.get("target_type")).isEqualTo("USER");
		assertThat(log.get("ip_address")).isEqualTo("127.0.0.1");
		assertThat(data(log, "before_data")).containsExactly(Map.entry("status", "ACTIVE"));
		assertThat(data(log, "after_data")).containsEntry("status", "DISABLED").containsEntry("username", "bob");
	}

	@Test
	void enablingBringsTheRolesBackWithNoNewGrant() throws Exception {
		long adam = account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR");
		String tokenOfBob = tokenOf("bob");
		disable(bob);

		enableUser(tokenOf("adam"), bob)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		assertThat(statusOf(bob)).isEqualTo("ACTIVE");
		me(tokenOfBob).andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("OPERATOR")));
		assertThat(roleLogs(bob)).as("no role was granted again").isEmpty();
		List<Map<String, Object>> logs = accountLogs(bob);
		assertThat(logs).hasSize(1);
		assertThat(logs.get(0).get("operator_id")).isEqualTo(adam);
		assertThat(data(logs.get(0), "before_data")).containsExactly(Map.entry("status", "DISABLED"));
		assertThat(data(logs.get(0), "after_data")).containsEntry("status", "ACTIVE").containsEntry("username", "bob");
	}

	/** A status change starts from one status. A request that finds the other says which it is and changes nothing. */
	@Test
	void disablingADisabledUserOrEnablingAnActiveOneIsRefusedAndSaysTheStatus() throws Exception {
		account("adam", "ADMIN");
		long active = account("active", "VIEWER");
		long off = account("off", "VIEWER");
		disable(off);
		String token = tokenOf("adam");

		disableUser(token, off)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER_CHANGED"))
				.andExpect(jsonPath("$.userStatus").value("DISABLED"))
				.andExpect(jsonPath("$.status").value(409));
		enableUser(token, active)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USER_CHANGED"))
				.andExpect(jsonPath("$.userStatus").value("ACTIVE"));

		assertThat(statusOf(off)).isEqualTo("DISABLED");
		assertThat(statusOf(active)).isEqualTo("ACTIVE");
		assertThat(accountLogs(off)).isEmpty();
		assertThat(accountLogs(active)).isEmpty();
	}

	@Test
	void aSoftDeletionMarksTheAccountRemovedAndDisabledAndKeepsItsRowAndRoles() throws Exception {
		long adam = account("adam", "ADMIN");
		long bob = account("bob", "OPERATOR");
		String tokenOfBob = tokenOf("bob");
		String token = tokenOf("adam");

		deleteUser(token, bob).andExpect(status().isNoContent());

		assertThat(isDeleted(bob)).isTrue();
		assertThat(statusOf(bob)).isEqualTo("DISABLED");
		assertThat(rolesOf(bob)).containsExactly("OPERATOR");
		assertThat(root().queryForObject("SELECT username FROM users WHERE id = ?", String.class, bob)).isEqualTo("bob");
		me(tokenOfBob).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		List<Map<String, Object>> logs = accountLogs(bob);
		assertThat(logs).hasSize(1);
		assertThat(logs.get(0).get("action")).isEqualTo("USER_DELETED");
		assertThat(logs.get(0).get("operator_id")).isEqualTo(adam);
		assertThat(data(logs.get(0), "before_data")).containsEntry("status", "ACTIVE").containsEntry("deleted", false);
		assertThat(data(logs.get(0), "after_data")).containsEntry("status", "DISABLED").containsEntry("deleted", true)
				.containsEntry("username", "bob");

		// For every later change the user does not exist.
		deleteUser(token, bob).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		enableUser(token, bob).andExpect(status().isNotFound());
		disableUser(token, bob).andExpect(status().isNotFound());
		assertThat(accountLogs(bob)).hasSize(1);
	}

	@Test
	void aDisabledUserCanBeSoftDeletedAndTheLogSaysWhatTheStatusWas() throws Exception {
		account("adam", "ADMIN");
		long bob = account("bob");
		disable(bob);

		deleteUser(tokenOf("adam"), bob).andExpect(status().isNoContent());

		assertThat(isDeleted(bob)).isTrue();
		assertThat(data(accountLogs(bob).get(0), "before_data")).containsEntry("status", "DISABLED").containsEntry("deleted", false);
	}

	@Test
	void aManagerChangesTheStatusOfUsersWithNoProtectedRoleOnly() throws Exception {
		account("mary", "MANAGER");
		long oscar = account("oscar", "OPERATOR");
		long nora = account("nora");
		long max = account("max", "MANAGER");
		long adam = account("adam", "ADMIN");
		String token = tokenOf("mary");

		disableUser(token, oscar).andExpect(status().isOk());
		enableUser(token, oscar).andExpect(status().isOk());
		deleteUser(token, nora).andExpect(status().isNoContent());

		for (long protectedUser : new long[] { max, adam }) {
			forbidden(disableUser(token, protectedUser));
			forbidden(deleteUser(token, protectedUser));
			disable(protectedUser);
			forbidden(enableUser(token, protectedUser));
			assertThat(isDeleted(protectedUser)).isFalse();
			assertThat(accountLogs(protectedUser)).isEmpty();
		}
	}

	@Test
	void nobodyDisablesOrDeletesTheirOwnAccount() throws Exception {
		long adam = account("adam", "ADMIN");
		long mary = account("mary", "MANAGER");

		forbidden(disableUser(tokenOf("adam"), adam))
				.andExpect(jsonPath("$.detail").value("Nobody changes their own account. Another administrator must do it"));
		forbidden(deleteUser(tokenOf("adam"), adam));
		forbidden(disableUser(tokenOf("mary"), mary));
		forbidden(deleteUser(tokenOf("mary"), mary));

		assertThat(statusOf(adam)).isEqualTo("ACTIVE");
		assertThat(isDeleted(mary)).isFalse();
	}

	@Test
	void anOperatorAViewerAndAUserWithNoRoleCannotCallTheEndpoints() throws Exception {
		account("oscar", "OPERATOR");
		account("vera", "VIEWER");
		account("nora");
		long bob = account("bob");

		for (String name : new String[] { "oscar", "vera" }) {
			String token = tokenOf(name);
			forbidden(disableUser(token, bob));
			forbidden(enableUser(token, bob));
			forbidden(deleteUser(token, bob));
		}
		String noRole = tokenOf("nora");
		disableUser(noRole, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));
		deleteUser(noRole, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));
		mvc.perform(post("/api/v1/users/" + bob + "/disable")).andExpect(status().isUnauthorized());

		assertThat(statusOf(bob)).isEqualTo("ACTIVE");
		assertThat(isDeleted(bob)).isFalse();
	}

	/** The SYSTEM identity is not a person: it cannot be disabled, enabled, or deleted (T-36). */
	@Test
	void aUserWhoDoesNotExistAndTheSystemIdentityAreNotFound() throws Exception {
		account("adam", "ADMIN");
		String token = tokenOf("adam");
		long system = systemIdentity();

		for (long id : new long[] { 999_999, system }) {
			disableUser(token, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
			enableUser(token, id).andExpect(status().isNotFound());
			deleteUser(token, id).andExpect(status().isNotFound());
		}

		assertThat(statusOf(system)).isEqualTo("ACTIVE");
		assertThat(isDeleted(system)).isFalse();
		assertThat(accountLogs(system)).isEmpty();
	}

	@Test
	void anAdministratorDisablesAndDeletesAnotherAdministratorAndOneIsLeft() throws Exception {
		account("adam", "ADMIN");
		long eve = account("eve", "ADMIN");
		long ed = account("ed", "ADMIN");
		String token = tokenOf("adam");

		disableUser(token, eve).andExpect(status().isOk());
		deleteUser(token, ed).andExpect(status().isNoContent());
		deleteUser(token, eve).andExpect(status().isNoContent());

		assertThat(usableAdministrators()).isEqualTo(1);
	}

	/** Step 3: the status and the roles of the actor are read again under the lock. */
	@Test
	void anActorWhoLostTheAuthorityAfterTheRequestArrivedIsRefused() {
		long mary = account("mary", "MANAGER");
		long nora = account("nora");
		long dan = account("dan", "ADMIN");
		disable(dan);
		long gone = account("gone", "ADMIN");
		softDelete(gone);
		long max = account("max", "MANAGER");
		long bob = account("bob");

		assertRefused(ErrorCode.FORBIDDEN, () -> users.disable(actor(mary), max, null));
		assertRefused(ErrorCode.FORBIDDEN, () -> users.softDelete(actor(mary), max, null));
		assertRefused(ErrorCode.ACCOUNT_NO_ROLE, () -> users.disable(actor(nora), bob, null));
		assertRefused(ErrorCode.ACCOUNT_DISABLED, () -> users.softDelete(actor(dan), bob, null));
		assertRefused(ErrorCode.UNAUTHENTICATED, () -> users.disable(actor(gone), bob, null));

		assertThat(statusOf(bob)).isEqualTo("ACTIVE");
		assertThat(statusOf(max)).isEqualTo("ACTIVE");
		assertThat(accountLogs(bob)).isEmpty();
	}

	private ResultActions me(String token) throws Exception {
		return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
	}

	private static ResultActions forbidden(ResultActions answer) throws Exception {
		return answer.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private static void assertRefused(ErrorCode code, Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
	}

}
