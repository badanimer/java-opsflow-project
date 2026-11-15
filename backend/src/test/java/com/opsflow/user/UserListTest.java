package com.opsflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * The user page (T-48, T-45; see User page and Home page and paging in docs/database/database-design.md):
 * who sees whom, the search and the role buttons, and the paging by key. Through HTTP calls against the
 * MySQL container.
 */
class UserListTest extends UserIntegrationSupport {

	@Test
	void anAdministratorSeesEveryUserFromAToZButTheSystemIdentityAndTheDeleted() throws Exception {
		long adam = account("adam", "ADMIN");
		long mary = account("mary", "MANAGER");
		long oscar = account("oscar", "OPERATOR", "VIEWER");
		long nora = account("nora");
		long dan = account("dan", "VIEWER");
		disable(dan);
		softDelete(account("gone", "OPERATOR"));

		list(tokenOf("adam"), "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].username", contains("adam", "dan", "mary", "nora", "oscar")))
				.andExpect(jsonPath("$.items[0].id").value(adam))
				.andExpect(jsonPath("$.items[0].roles", contains("ADMIN")))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.items[1].id").value(dan))
				.andExpect(jsonPath("$.items[1].status").value("DISABLED"))
				.andExpect(jsonPath("$.items[2].id").value(mary))
				.andExpect(jsonPath("$.items[3].id").value(nora))
				.andExpect(jsonPath("$.items[3].roles", hasSize(0)))
				.andExpect(jsonPath("$.items[4].id").value(oscar))
				.andExpect(jsonPath("$.items[4].roles", contains("OPERATOR", "VIEWER")))
				.andExpect(jsonPath("$.nextCursor").value(nullValue()))
				.andExpect(jsonPath("$.prevCursor").value(nullValue()));
	}

	/** A manager sees only the users they may manage: none who holds ADMIN or MANAGER, and not their own account. */
	@Test
	void aManagerSeesOnlyUsersWhoHoldNoProtectedRole() throws Exception {
		account("adam", "ADMIN");
		account("mary", "MANAGER");
		account("max", "MANAGER");
		account("mixed", "MANAGER", "OPERATOR");
		account("oscar", "OPERATOR");
		account("nora");
		disable(account("dan", "VIEWER"));
		softDelete(account("gone"));

		list(tokenOf("mary"), "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].username", contains("dan", "nora", "oscar")));
	}

	@Test
	void theSearchFindsAPartOfTheUsernameWithoutRegardToCase() throws Exception {
		account("adam", "ADMIN");
		account("alice");
		account("Natalie", "VIEWER");
		account("bob");
		String token = tokenOf("adam");

		list(token, "search=ali").andExpect(jsonPath("$.items[*].username", contains("alice", "Natalie")));
		list(token, "search=ALI").andExpect(jsonPath("$.items[*].username", contains("alice", "Natalie")));
		list(token, "search= bo ").andExpect(jsonPath("$.items[*].username", contains("bob")));
		list(token, "search= ").andExpect(jsonPath("$.items", hasSize(4)));
		// The characters of a LIKE pattern match only themselves, and no username holds them.
		list(token, "search=%").andExpect(jsonPath("$.items", hasSize(0)));
		list(token, "search=_").andExpect(jsonPath("$.items", hasSize(0)));
		list(token, "search=!").andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void theRoleButtonsNarrowTheListToTheHoldersOfAnyChosenRole() throws Exception {
		account("adam", "ADMIN");
		account("mary", "MANAGER");
		account("oscar", "OPERATOR");
		account("olga", "OPERATOR", "VIEWER");
		account("vera", "VIEWER");
		account("nora");
		String token = tokenOf("adam");

		list(token, "roles=OPERATOR").andExpect(jsonPath("$.items[*].username", contains("olga", "oscar")));
		list(token, "roles=VIEWER,OPERATOR").andExpect(jsonPath("$.items[*].username", contains("olga", "oscar", "vera")));
		list(token, "roles=NONE").andExpect(jsonPath("$.items[*].username", contains("nora")));
		list(token, "roles=NONE&roles=MANAGER").andExpect(jsonPath("$.items[*].username", contains("mary", "nora")));
		list(token, "roles=admin").andExpect(jsonPath("$.items[*].username", contains("adam")));
		list(token, "roles=VIEWER&search=v").andExpect(jsonPath("$.items[*].username", contains("vera")));
	}

	/** Neither filter widens the list. */
	@Test
	void aManagerWhoSearchesOrFiltersForAProtectedUserFindsNothing() throws Exception {
		account("adam", "ADMIN");
		account("mary", "MANAGER");
		account("max", "MANAGER");
		account("oscar", "OPERATOR");
		String token = tokenOf("mary");

		list(token, "search=adam").andExpect(jsonPath("$.items", hasSize(0)));
		list(token, "search=mary").andExpect(jsonPath("$.items", hasSize(0)));
		list(token, "roles=ADMIN,MANAGER").andExpect(jsonPath("$.items", hasSize(0)));
		list(token, "roles=MANAGER,OPERATOR").andExpect(jsonPath("$.items[*].username", contains("oscar")));
	}

	@Test
	void anOperatorAViewerAndAUserWithNoRoleCannotOpenTheList() throws Exception {
		account("oscar", "OPERATOR");
		account("vera", "VIEWER");
		account("nora");

		list(tokenOf("oscar"), "").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		list(tokenOf("vera"), "").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		list(tokenOf("nora"), "").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NO_ROLE"));
		mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void aParameterThatIsNotRightIsAValidationError() throws Exception {
		account("adam", "ADMIN");
		String token = tokenOf("adam");

		for (String query : new String[] { "roles=AUDITOR", "roles=OPERATOR,AUDITOR", "search=" + "a".repeat(65), "limit=0", "limit=11",
				"order=asc", "direction=prev", "direction=up", "cursor=garbage!" }) {
			list(token, query)
					.andExpect(result -> assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400))
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		list(token, "search=" + "a".repeat(64)).andExpect(status().isOk());
		// An empty value is no button chosen.
		list(token, "roles=").andExpect(status().isOk()).andExpect(jsonPath("$.items[*].username", contains("adam")));
	}

	/** Previous and next only: the first page has no previous, the last no next, and a round trip returns the same rows. */
	@Test
	void theListIsPagedByKeyForwardAndBack() throws Exception {
		account("adam", "ADMIN");
		for (String name : new String[] { "u01", "u02", "u03", "u04", "u05", "u06", "u07" }) {
			account(name);
		}
		String token = tokenOf("adam");

		String first = body(list(token, "limit=3")
				.andExpect(jsonPath("$.items[*].username", contains("adam", "u01", "u02")))
				.andExpect(jsonPath("$.prevCursor").value(nullValue())));
		String second = body(list(token, "limit=3&cursor=" + next(first))
				.andExpect(jsonPath("$.items[*].username", contains("u03", "u04", "u05"))));
		String last = body(list(token, "limit=3&cursor=" + next(second))
				.andExpect(jsonPath("$.items[*].username", contains("u06", "u07")))
				.andExpect(jsonPath("$.nextCursor").value(nullValue())));

		String back = body(list(token, "limit=3&direction=prev&cursor=" + prev(last))
				.andExpect(jsonPath("$.items[*].username", contains("u03", "u04", "u05"))));
		list(token, "limit=3&direction=prev&cursor=" + prev(back))
				.andExpect(jsonPath("$.items[*].username", contains("adam", "u01", "u02")))
				.andExpect(jsonPath("$.prevCursor").value(nullValue()));
	}

	/** A user added between two page reads shows on a later page if it sorts there, and no row is shown twice or skipped. */
	@Test
	void aUserAddedBetweenTwoPageReadsIsNeitherDoubledNorSkipped() throws Exception {
		account("adam", "ADMIN");
		for (String name : new String[] { "u02", "u04", "u06", "u08" }) {
			account(name);
		}
		String token = tokenOf("adam");
		String first = body(list(token, "limit=2").andExpect(jsonPath("$.items[*].username", contains("adam", "u02"))));

		account("u01"); // before the cursor: on no later page
		account("u05"); // after it

		List<String> rest = new ArrayList<>();
		String cursor = next(first);
		while (cursor != null) {
			String page = body(list(token, "limit=2&cursor=" + cursor));
			rest.addAll(JsonPath.read(page, "$.items[*].username"));
			cursor = next(page);
		}
		assertThat(rest).containsExactly("u04", "u05", "u06", "u08");
	}

	/** A filtered list keeps its filters on every page, and the cursor moves through the filtered rows only. */
	@Test
	void theFiltersStayTheSameOnEveryPage() throws Exception {
		account("adam", "ADMIN");
		for (String name : new String[] { "op1", "op2", "op3", "vw1" }) {
			account(name, name.startsWith("op") ? "OPERATOR" : "VIEWER");
		}
		String token = tokenOf("adam");

		String first = body(list(token, "limit=2&roles=OPERATOR").andExpect(jsonPath("$.items[*].username", contains("op1", "op2"))));
		list(token, "limit=2&roles=OPERATOR&cursor=" + next(first))
				.andExpect(jsonPath("$.items[*].username", contains("op3")))
				.andExpect(jsonPath("$.nextCursor").value(nullValue()));
	}

	/** {@code GET /users} with the parameters of a query written as {@code name=value&...}, the values as typed and not encoded. */
	private ResultActions list(String token, String query) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/users").header("Authorization", "Bearer " + token);
		for (String pair : query.isEmpty() ? new String[0] : query.split("&")) {
			int equals = pair.indexOf('=');
			request.param(pair.substring(0, equals), pair.substring(equals + 1));
		}
		return mvc.perform(request);
	}

	private static String body(ResultActions answer) throws Exception {
		return answer.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String next(String page) {
		return JsonPath.read(page, "$.nextCursor");
	}

	private static String prev(String page) {
		return JsonPath.read(page, "$.prevCursor");
	}

}
