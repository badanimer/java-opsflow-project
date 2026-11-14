package com.opsflow.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The first gate of the Permission Matrix (T-27, T-22, T-19): a request reaches a controller method only
 * when that method declares that the caller may. Through HTTP calls against the MySQL container, with
 * the endpoints of {@link PermissionProbeController}: {@code /managers} needs a permission of
 * administrators and managers, {@code /operators} one that operators have and viewers do not, and
 * {@code /viewers} one that viewers have and operators do not.
 */
class EndpointPermissionTest extends AuthIntegrationSupport {

	private static final String PROBE = "/api/v1/permission-probe";

	@Test
	void aPublicEndpointNeedsNoToken() throws Exception {
		mvc.perform(get(PROBE + "/public")).andExpect(status().isOk());
	}

	@Test
	void withoutATokenEveryOtherEndpointIsUnauthenticated() throws Exception {
		for (String path : new String[] { "/any-account", "/managers", "/operators", "/undeclared", "/no-such-endpoint" }) {
			mvc.perform(get(PROBE + path))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}
	}

	@Test
	void aUserWithNoRoleReachesAnEndpointForAnyAccount() throws Exception {
		String token = tokenOfNew("alice");

		call(token, "/any-account").andExpect(status().isOk());
	}

	@Test
	void aUserWithNoRoleIsToldSoOnAnEndpointThatNeedsARole() throws Exception {
		String token = tokenOfNew("alice");

		for (String path : new String[] { "/managers", "/operators", "/viewers" }) {
			call(token, path)
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"))
					.andExpect(jsonPath("$.detail").value("This account is waiting for an administrator to assign permissions"))
					.andExpect(jsonPath("$.requestId").isNotEmpty());
		}
	}

	@Test
	void aUserWhoseRolesDoNotGrantThePermissionIsForbidden() throws Exception {
		String token = tokenOfNew("victor", "VIEWER");

		call(token, "/managers")
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
		call(token, "/operators").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void everyRoleThatGrantsThePermissionGetsThrough() throws Exception {
		call(tokenOfNew("adam", "ADMIN"), "/managers").andExpect(status().isOk());
		call(tokenOfNew("mary", "MANAGER"), "/managers").andExpect(status().isOk());
		call(tokenOfNew("oscar", "OPERATOR"), "/operators").andExpect(status().isOk());
		call(tokenOfNew("vera", "VIEWER"), "/viewers").andExpect(status().isOk());
	}

	/** The roles are not a ladder: an operator may do things a viewer may not, and a viewer things an operator may not. */
	@Test
	void aRoleOpensOnlyWhatItIsGranted() throws Exception {
		String token = tokenOfNew("oscar", "OPERATOR");

		call(token, "/operators").andExpect(status().isOk());
		call(token, "/viewers").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		call(token, "/managers").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void theRolesOfAUserAddUp() throws Exception {
		String token = tokenOfNew("olivia", "OPERATOR", "VIEWER");

		call(token, "/operators").andExpect(status().isOk());
		call(token, "/viewers").andExpect(status().isOk());
		call(token, "/managers").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void anEndpointThatDeclaresNothingIsClosedEvenForAnAdministrator() throws Exception {
		String token = tokenOfNew("adam", "ADMIN");

		call(token, "/undeclared").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	/** The answer for an endpoint that declares nothing does not depend on the roles, so it is not ACCOUNT_NO_ROLE. */
	@Test
	void anEndpointThatDeclaresNothingIsForbiddenForAUserWithNoRole() throws Exception {
		String token = tokenOfNew("alice");

		call(token, "/undeclared").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void aPathThatExistsOnlyWithAnotherMethodIsRefused() throws Exception {
		String token = tokenOfNew("mary", "MANAGER");

		mvc.perform(post(PROBE + "/managers").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void aRoleThatIsGrantedOrTakenAwayCountsOnTheNextRequest() throws Exception {
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);
		call(token, "/operators").andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));

		grant(alice, "OPERATOR");
		call(token, "/operators").andExpect(status().isOk());

		root().update("DELETE FROM user_roles WHERE user_id = ?", alice);
		call(token, "/operators").andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));
	}

	/** A role counts only for a usable account: the roles stay in the table, and the account is refused. */
	@Test
	void theRolesOfADisabledAccountOpenNothing() throws Exception {
		long adam = account("adam");
		grant(adam, "ADMIN");
		String token = tokenOf("adam", PASSWORD);
		call(token, "/managers").andExpect(status().isOk());

		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", adam);

		call(token, "/managers").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
		call(token, "/any-account").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
	}

	private String tokenOfNew(String username, String... roleCodes) throws Exception {
		long id = account(username);
		for (String code : roleCodes) {
			grant(id, code);
		}
		return tokenOf(username, PASSWORD);
	}

	private ResultActions call(String token, String path) throws Exception {
		return mvc.perform(get(PROBE + path).header("Authorization", "Bearer " + token));
	}

}
