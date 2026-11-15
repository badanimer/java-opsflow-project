package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;

/**
 * One signed-in device per account (see One signed-in device in docs/database/database-design.md):
 * the newest login ends the earlier ones, whose next request is refused with SESSION_REPLACED, and whose
 * open event streams are told at once.
 */
class SessionTest extends AuthIntegrationSupport {

	@Autowired
	SessionEvents sessionEvents;

	@Autowired
	AuthRepository repository;

	@Test
	void aSecondLoginEndsTheTokenOfTheFirst() throws Exception {
		account("alice");
		String first = tokenOf("alice", PASSWORD);
		me(first).andExpect(status().isOk());

		String second = tokenOf("alice", PASSWORD);

		me(first).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_REPLACED"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
		me(second).andExpect(status().isOk());
	}

	@Test
	void theTokenOfASignUpIsEndedByALaterLogin() throws Exception {
		String email = "alice@example.com";
		String body = signUp("alice", email, PASSWORD, codeFor(email, "REGISTER")).andReturn().getResponse().getContentAsString();
		String signUpToken = JsonPath.read(body, "$.token");
		me(signUpToken).andExpect(status().isOk());

		tokenOf("alice", PASSWORD);

		me(signUpToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
	}

	@Test
	void aLoginOfAnotherAccountEndsNothing() throws Exception {
		account("alice");
		account("bob");
		String alice = tokenOf("alice", PASSWORD);
		tokenOf("bob", PASSWORD);
		me(alice).andExpect(status().isOk());
	}

	@Test
	void aDisabledAccountIsToldThatItIsDisabledAndNotThatItWasReplaced() throws Exception {
		long alice = account("alice");
		String first = tokenOf("alice", PASSWORD);
		tokenOf("alice", PASSWORD);
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", alice);

		me(first).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
	}

	@Test
	void theOpenStreamOfTheFirstLoginGetsTheEventAtOnceAndEnds() throws Exception {
		account("alice");
		String first = tokenOf("alice", PASSWORD);
		MvcResult stream = mvc.perform(get("/api/v1/auth/session-events").header("Authorization", "Bearer " + first))
				.andExpect(request().asyncStarted())
				.andReturn();
		assertThat(stream.getResponse().getContentAsString()).as("the stream has started").contains(":open");
		assertThat(stream.getResponse().getContentAsString()).doesNotContain("session-replaced");

		tokenOf("alice", PASSWORD);

		stream.getAsyncResult(TimeUnit.SECONDS.toMillis(5));
		assertThat(stream.getResponse().getContentAsString()).contains("event:session-replaced");
		assertThat(sessionEvents.openStreams()).isZero();
	}

	@Test
	void theStreamOfTheNewestLoginStaysOpen() throws Exception {
		account("alice");
		tokenOf("alice", PASSWORD);
		String newest = tokenOf("alice", PASSWORD);
		MvcResult stream = mvc.perform(get("/api/v1/auth/session-events").header("Authorization", "Bearer " + newest))
				.andExpect(request().asyncStarted())
				.andReturn();

		assertThat(stream.getResponse().getContentAsString()).doesNotContain("session-replaced");
		assertThat(sessionEvents.openStreams()).isEqualTo(1);
		sessionEvents.loggedIn(Long.MAX_VALUE, "another-account");
		assertThat(sessionEvents.openStreams()).isEqualTo(1);
	}

	@Test
	void aStreamCannotBeOpenedWithTheTokenOfAnEndedLogin() throws Exception {
		account("alice");
		String first = tokenOf("alice", PASSWORD);
		tokenOf("alice", PASSWORD);

		mvc.perform(get("/api/v1/auth/session-events").header("Authorization", "Bearer " + first))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
	}

	@Test
	void aStreamOpenedJustAfterALoginItMissedIsToldAtOnce() throws Exception {
		long alice = account("alice");
		String first = tokenOf("alice", PASSWORD);
		String firstSession = repository.sessionId(alice);
		// The token check of the stream passed, then a login committed before the stream was registered.
		tokenOf("alice", PASSWORD);

		SseEmitter emitter = sessionEvents.open(alice, firstSession);

		assertThat(emitter).isNotNull();
		assertThat(sessionEvents.openStreams()).as("the stream was ended at once").isZero();
		me(first).andExpect(status().isUnauthorized());
	}

	@Test
	void ofTwoLoginsAtTheSameMomentExactlyOneTokenStaysValid() throws Exception {
		account("alice");
		for (int round = 0; round < 5; round++) {
			List<Outcome<String>> outcomes = RaceRunner.run(2, () -> tokenOf("alice", PASSWORD));
			assertThat(outcomes).allMatch(Outcome::succeeded);

			int valid = 0;
			for (Outcome<String> outcome : outcomes) {
				if (me(outcome.value()).andReturn().getResponse().getStatus() == 200) {
					valid++;
				}
			}
			assertThat(valid).as("round %d: the login that committed last is the one signed in", round).isEqualTo(1);
		}
	}

}
