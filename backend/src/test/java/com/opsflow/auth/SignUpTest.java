package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.support.RaceRunner;

/**
 * Sign-up through HTTP, against the MySQL container: the account it creates, the refusals of a wrong,
 * dead, or foreign code, the rules for the username and the password, and the races of Concurrency by
 * Module (one code sent twice, and one username taken twice).
 */
class SignUpTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final String EMAIL = "alice@example.com";

	@Test
	void aSignUpCreatesAnActiveAccountWithNoRole() throws Exception {
		String code = codeFor(EMAIL, "REGISTER");

		String body = signUp("alice", EMAIL, PASSWORD, code)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.expiresAt").exists())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		String token = JsonPath.read(body, "$.token");

		Map<String, Object> user = root().queryForMap(
				"SELECT username, email, password_hash, status, deleted, is_system, password_changed_at FROM users WHERE id = ?", id);
		assertThat(user.get("username")).isEqualTo("alice");
		assertThat(user.get("email")).isEqualTo(EMAIL);
		assertThat((String) user.get("password_hash")).startsWith("{bcrypt}").doesNotContain(PASSWORD);
		assertThat(user.get("status")).isEqualTo("ACTIVE");
		assertThat(user.get("deleted")).isEqualTo(false);
		assertThat(user.get("is_system")).isEqualTo(false);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id = ?", Integer.class, id)).isZero();
		assertThat(codeRow(EMAIL, "REGISTER").get("consumed_at")).isNotNull();

		// The new user is the operator of the log row, and the row holds no password and no code.
		Map<String, Object> log = root().queryForMap(
				"SELECT operator_id, category, target_type, CAST(after_data AS CHAR) AS after_data FROM operation_logs WHERE action = 'USER_REGISTERED' AND target_id = ?",
				id);
		assertThat(((Number) log.get("operator_id")).longValue()).isEqualTo(id);
		assertThat(log.get("category")).isEqualTo("ACCOUNT");
		assertThat(log.get("target_type")).isEqualTo("USER");
		assertThat((String) log.get("after_data")).contains("alice").contains(EMAIL).doesNotContain(PASSWORD).doesNotContain(code);

		// The new user is logged in by the sign-up itself and waits for a role: the token opens the account,
		// with no role, which sends the frontend to the pending page.
		me(token).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.roles").isEmpty());
		assertThat(lastLoginAt(id)).isNotNull();
		// The password works for a later login too.
		me(tokenOf("alice", PASSWORD)).andExpect(status().isOk());
	}

	/**
	 * The clock stands still, so the sign-up and the reset fall in the same millisecond. The token that the
	 * sign-up gave must still end with the reset.
	 */
	@Test
	void theTokenOfASignUpEndsWithAPasswordResetInTheSameMillisecond() throws Exception {
		clock.set(NOW);
		String body = signUp("alice", EMAIL, PASSWORD, codeFor(EMAIL, "REGISTER")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		String token = JsonPath.read(body, "$.token");
		me(token).andExpect(status().isOk());

		resetPassword(id, "new-horse-2");

		me(token).andExpect(status().isUnauthorized());
	}

	@Test
	void aWrongCodeIsRefusedAndCounted() throws Exception {
		String code = codeFor(EMAIL, "REGISTER");

		signUp("alice", EMAIL, PASSWORD, otherThan(code))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));

		assertThat(wrongEntries(EMAIL, "REGISTER")).isEqualTo(1);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM users WHERE username = 'alice'", Integer.class)).isZero();
		// The right code still works after one wrong entry.
		signUp("alice", EMAIL, PASSWORD, code).andExpect(status().isCreated());
	}

	@Test
	void afterFiveWrongEntriesTheRightCodeIsRefused() throws Exception {
		String code = codeFor(EMAIL, "REGISTER");
		for (int i = 0; i < 5; i++) {
			signUp("alice", EMAIL, PASSWORD, otherThan(code)).andExpect(status().isBadRequest());
		}

		signUp("alice", EMAIL, PASSWORD, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALID"));

		// A dead code is not counted further.
		assertThat(wrongEntries(EMAIL, "REGISTER")).isEqualTo(5);
	}

	@Test
	void aCodeIsRefusedFromTheMomentItExpires() throws Exception {
		clock.set(NOW);
		String code = codeFor(EMAIL, "REGISTER");

		clock.advance(Duration.ofMinutes(10));

		signUp("alice", EMAIL, PASSWORD, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALID"));
	}

	@Test
	void onlyTheLatestCodeWorks() throws Exception {
		clock.set(NOW);
		String first = codeFor(EMAIL, "REGISTER");
		clock.advance(Duration.ofSeconds(60));
		String second = codeFor(EMAIL, "REGISTER");

		if (!first.equals(second)) {
			signUp("alice", EMAIL, PASSWORD, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALID"));
		}
		signUp("alice", EMAIL, PASSWORD, second).andExpect(status().isCreated());
	}

	/** A code is bound to its purpose and its address. */
	@Test
	void aCodeForAnotherPurposeOrAnotherAddressIsRefused() throws Exception {
		account("bob");
		String resetCode = codeFor("bob@example.com", "RESET_PASSWORD");
		String aliceCode = codeFor(EMAIL, "REGISTER");

		signUp("bob2", "bob@example.com", PASSWORD, resetCode).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));
		signUp("carol", "carol@example.com", PASSWORD, aliceCode).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));
	}

	@Test
	void aBadUsernameOrPasswordIsRefusedBeforeTheCodeIsLookedAt() throws Exception {
		String code = codeFor(EMAIL, "REGISTER");

		for (String password : List.of("short-1", "onlyletters", "1234567890")) {
			signUp("alice", EMAIL, password, otherThan(code))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[0].field").value("password"));
		}
		for (String username : List.of("al", "alice smith", "alice@home", "alice.smith", "alice_1", "alice-1")) {
			signUp(username, EMAIL, PASSWORD, otherThan(code))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[0].field").value("username"));
		}
		signUp("alice", EMAIL, PASSWORD, "12345").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("code"));

		// None of these was an entry of the code.
		assertThat(wrongEntries(EMAIL, "REGISTER")).isZero();
	}

	/** The refusal rolls the transaction back, so the person picks another name and sends the same code. */
	@Test
	void aTakenUsernameLeavesTheCodeUsable() throws Exception {
		account("bob");
		String code = codeFor(EMAIL, "REGISTER");

		// Usernames that differ only in case are the same, and the name of the SYSTEM identity is taken.
		for (String taken : List.of("bob", "BOB", "System")) {
			signUp(taken, EMAIL, PASSWORD, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
		}

		assertThat(codeRow(EMAIL, "REGISTER").get("consumed_at")).isNull();
		assertThat(wrongEntries(EMAIL, "REGISTER")).isZero();
		signUp("alice", EMAIL, PASSWORD, code).andExpect(status().isCreated());
	}

	/** The address got an account after the code was mailed, here directly in the database. Before that the request for a code would have said so. */
	@Test
	void anAddressThatGotAnAccountInTheMeantimeIsRefused() throws Exception {
		String code = codeFor("bob@example.com", "REGISTER");
		account("bob");

		signUp("bobby", "bob@example.com", PASSWORD, code).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void oneCodeSentTwiceAtTheSameMomentCreatesOneAccount() throws Exception {
		String code = codeFor(EMAIL, "REGISTER");

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(4,
				() -> signUp("alice", EMAIL, PASSWORD, code).andReturn().getResponse().getStatus());

		assertThat(outcomes).allMatch(RaceRunner.Outcome::succeeded);
		assertThat(outcomes).extracting(RaceRunner.Outcome::value).filteredOn(status -> status == 201).hasSize(1);
		// The others found the code used, the username taken, or the address taken, whichever they saw first.
		assertThat(outcomes).extracting(RaceRunner.Outcome::value).allMatch(status -> status == 201 || status == 400 || status == 409);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM users WHERE username = 'alice'", Integer.class)).isEqualTo(1);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action = 'USER_REGISTERED'", Integer.class))
				.isEqualTo(1);
	}

	/** Two people, each with a code of their own, want the same username. The unique key decides. */
	@Test
	void oneUsernameTakenTwiceAtTheSameMomentCreatesOneAccount() throws Exception {
		List<String> emails = List.of("one@example.com", "two@example.com");
		List<String> codes = List.of(codeFor(emails.get(0), "REGISTER"), codeFor(emails.get(1), "REGISTER"));
		AtomicInteger next = new AtomicInteger();

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(2, () -> {
			int i = next.getAndIncrement();
			return signUp("alice", emails.get(i), PASSWORD, codes.get(i)).andReturn().getResponse().getStatus();
		});

		assertThat(outcomes).extracting(RaceRunner.Outcome::value).containsExactlyInAnyOrder(201, 409);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM users WHERE username = 'alice'", Integer.class)).isEqualTo(1);
		// The code of the one who lost is still unused, so it works with another name.
		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes WHERE consumed_at IS NULL", Integer.class))
				.isEqualTo(1);
		String winner = root().queryForObject("SELECT email FROM users WHERE username = 'alice'", String.class);
		int loser = emails.indexOf(winner) == 0 ? 1 : 0;
		signUp("alice2", emails.get(loser), PASSWORD, codes.get(loser)).andExpect(status().isCreated());
	}

}
