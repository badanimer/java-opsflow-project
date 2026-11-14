package com.opsflow.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.opsflow.common.security.Role;
import com.opsflow.common.time.UtcTime;

/**
 * The check of the account on every request (T-20), through HTTP calls against the MySQL container. The
 * rules are in Token checks and Disabled accounts in docs/database/database-design.md.
 */
class TokenCheckTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	@Test
	void aRequestWithoutATokenIsUnauthenticatedInTheErrorFormatOfTheApi() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void aTokenThatThisServerDidNotSignIsRefusedAndTheBodyDoesNotSayWhy() throws Exception {
		long alice = account("alice");
		String foreign = new TokenService(Base64.getEncoder().encodeToString(new byte[32]), Duration.ofHours(12), clock)
				.issue(alice, clock.instant()).value();

		for (String token : new String[] { foreign, "not-a-token" }) {
			me(token).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
					.andExpect(jsonPath("$.detail").value("Log in to continue"));
		}
	}

	@Test
	void aTokenLastsTwelveHours() throws Exception {
		clock.set(NOW);
		account("alice");
		String token = tokenOf("alice", PASSWORD);

		clock.set(NOW.plus(Duration.ofHours(12)).minusSeconds(1));
		me(token).andExpect(status().isOk());

		clock.set(NOW.plus(Duration.ofHours(12)));
		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void anAccountDisabledAfterLoginIsRefusedOnItsNextRequest() throws Exception {
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);
		me(token).andExpect(status().isOk());

		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);

		me(token).andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
				.andExpect(jsonPath("$.detail").value("This account has been disabled. Please contact an administrator."))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	/**
	 * The server keeps no list of tokens, so it does not revoke one when an account is disabled and enabled
	 * again (decided in database v1.11). The user logs in again because the frontend dropped the token.
	 */
	@Test
	void aTokenThatSomebodyKeptWorksAgainAfterTheAccountIsEnabledAgain() throws Exception {
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);
		me(token).andExpect(status().isForbidden());

		root().update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", alice);

		me(token).andExpect(status().isOk());
	}

	@Test
	void aSoftDeletedAccountLosesItsTokenAsUnauthenticated() throws Exception {
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);

		root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", alice);

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void aTokenIsRefusedOnceThePasswordHasChanged() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);

		clock.advance(Duration.ofSeconds(30));
		resetPassword(alice, "new-horse-2");

		me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	/** The clock stands still, so the reset and both logins fall in the same millisecond. */
	@Test
	void inTheSameMillisecondATokenFromBeforeTheResetIsRefusedAndOneFromAfterItWorks() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String before = tokenOf("alice", PASSWORD);

		resetPassword(alice, "new-horse-2");
		String after = tokenOf("alice", "new-horse-2");

		me(before).andExpect(status().isUnauthorized());
		me(after).andExpect(status().isOk());
	}

	@Test
	void aTokenIssuedInTheSameSecondAsAnEarlierResetWorks() throws Exception {
		// The standard issue time of a token is in whole seconds; the reset happened 400 ms before the login.
		clock.set(NOW.plusMillis(500));
		long alice = account("alice");
		root().update("UPDATE users SET password_changed_at = ? WHERE id = ?", UtcTime.toLocalDateTime(NOW.plusMillis(100)), alice);

		me(tokenOf("alice", PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void theRolesAreReadFromTheDatabaseOnEveryRequest() throws Exception {
		long alice = account("alice");
		String token = tokenOf("alice", PASSWORD);
		me(token).andExpect(jsonPath("$.roles", hasSize(0))).andExpect(jsonPath("$.permissions", hasSize(0)));

		grant(alice, "OPERATOR");
		grant(alice, "MANAGER");

		me(token).andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", contains("MANAGER", "OPERATOR")))
				.andExpect(jsonPath("$.permissions", hasSize(Role.MANAGER.permissions().size())))
				.andExpect(jsonPath("$.permissions", hasItems("ORDER_PLACE", "AUDIT_VIEW", "ROLE_ASSIGN")));
	}

	@Test
	void aRequestThatNoRuleAllowsIsRefused() throws Exception {
		account("alice");
		String token = tokenOf("alice", PASSWORD);

		mvc.perform(get("/api/v1/not-declared-anywhere").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
		mvc.perform(get("/api/v1/not-declared-anywhere"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
