package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.opsflow.common.mail.OutgoingMail;
import com.opsflow.support.RaceRunner;

/**
 * Requesting an email code through HTTP, against the MySQL container: what is mailed and stored, the
 * same answer whether or not the address is registered, the 60-second rule, and the two limits on
 * requests (per client address in an hour, and for the whole system in a day).
 */
class EmailCodeTest extends AuthIntegrationSupport {

	/** Noon in Sydney, so an hour and the rest of the day lie ahead. */
	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final Instant NEXT_SYDNEY_MIDNIGHT = Instant.parse("2026-11-13T13:00:00Z");
	private static final String CLIENT = "203.0.113.7";

	@Test
	void aSignUpCodeIsMailedAndStoredAsAKeyedHash() throws Exception {
		clock.set(NOW);

		requestCode("new@example.com", "REGISTER").andExpect(status().isNoContent()).andExpect(content().string(""));

		List<OutgoingMail> mails = sentMail();
		assertThat(mails).hasSize(1);
		assertThat(mails.get(0).to()).isEqualTo("new@example.com");
		assertThat(mails.get(0).text()).contains("signing up").contains("10 minutes");
		String code = codeMailedTo("new@example.com");

		Map<String, Object> row = codeRow("new@example.com", "REGISTER");
		assertThat((String) row.get("code_hash")).matches("[0-9a-f]{64}").doesNotContain(code);
		assertThat(row.get("attempts")).isEqualTo(0);
		assertThat(row.get("consumed_at")).isNull();
		assertThat(row.get("created_at")).isEqualTo(LocalDateTime.of(2026, 11, 13, 1, 0));
		assertThat(row.get("expires_at")).isEqualTo(LocalDateTime.of(2026, 11, 13, 1, 10));
	}

	/** Sign-up says that the address has an account. Nothing is mailed, stored, or counted. */
	@Test
	void aSignUpCodeForAnAddressWithAnAccountIsRefusedAsEmailTaken() throws Exception {
		account("alice");
		long bob = account("bob");
		root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", bob);

		// A soft-deleted account and the SYSTEM identity keep their addresses, and the case does not matter.
		for (String email : List.of("alice@example.com", "ALICE@example.com", "bob@example.com", "system@opsflow.invalid")) {
			requestCode(email, "REGISTER")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
		}

		assertThat(sentMail()).isEmpty();
		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isZero();
		assertThat(requestCount("ALL")).isZero();
	}

	@Test
	void aCodeForANewPasswordIsMailedOnlyForAnAccountThatCanBeRecovered() throws Exception {
		account("alice");
		long bob = account("bob");
		root().update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", bob);
		long carol = account("carol");
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", carol);

		for (String email : List.of("alice@example.com", "bob@example.com", "carol@example.com", "nobody@example.com",
				"system@opsflow.invalid")) {
			requestCode(email, "RESET_PASSWORD").andExpect(status().isNoContent()).andExpect(content().string(""));
		}

		// A disabled account may set a new password; it stays disabled. A soft-deleted account, an address
		// with no account, and the SYSTEM identity get no mail.
		assertThat(sentMail()).extracting(OutgoingMail::to).containsExactly("alice@example.com", "carol@example.com");
		assertThat(sentMail().get(0).text()).contains("setting a new password");
		assertThat(codeRow("nobody@example.com", "RESET_PASSWORD").get("code_hash")).isEqualTo("!NOT-SENT");
	}

	@Test
	void aSecondRequestWithinSixtySecondsIsRefused() throws Exception {
		clock.set(NOW);
		requestCode("new@example.com", "REGISTER").andExpect(status().isNoContent());

		clock.advance(Duration.ofSeconds(59));
		requestCode("new@example.com", "REGISTER")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		// The rule is per address and purpose.
		requestCode("other@example.com", "REGISTER").andExpect(status().isNoContent());

		clock.advance(Duration.ofSeconds(1));
		requestCode("new@example.com", "REGISTER").andExpect(status().isNoContent());

		assertThat(sentMail()).extracting(OutgoingMail::to).containsExactly("new@example.com", "other@example.com", "new@example.com");
	}

	/** The rule also holds for an address that gets no mail, so the answer does not tell the two apart. */
	@Test
	void theSixtySecondRuleIsTheSameForAnAddressThatGetsNoMail() throws Exception {
		clock.set(NOW);
		requestCode("nobody@example.com", "RESET_PASSWORD").andExpect(status().isNoContent());

		requestCode("nobody@example.com", "RESET_PASSWORD").andExpect(status().isTooManyRequests());
	}

	@Test
	void aRefusedRequestIsNotCounted() throws Exception {
		clock.set(NOW);
		requestCode("new@example.com", "REGISTER", CLIENT).andExpect(status().isNoContent());

		requestCode("new@example.com", "REGISTER", CLIENT).andExpect(status().isTooManyRequests());

		assertThat(requestCount("IP:" + CLIENT)).isEqualTo(1);
		assertThat(requestCount("ALL")).isEqualTo(1);
	}

	@Test
	void theEleventhRequestOfOneClientAddressInAnHourIsRefused() throws Exception {
		clock.set(NOW);
		for (int i = 1; i <= 10; i++) {
			requestCode("person" + i + "@example.com", "REGISTER", CLIENT).andExpect(status().isNoContent());
		}

		requestCode("person11@example.com", "REGISTER", CLIENT)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

		assertThat(sentMail()).hasSize(10);
		assertThat(requestCount("IP:" + CLIENT)).isEqualTo(10);
		// Another client is not affected, and the next hour starts a new count.
		requestCode("person11@example.com", "REGISTER", "203.0.113.8").andExpect(status().isNoContent());
		clock.set(NOW.plus(Duration.ofHours(1)));
		requestCode("person12@example.com", "REGISTER", CLIENT).andExpect(status().isNoContent());
	}

	@Test
	void theRequestsOfTheWholeSystemAreLimitedForTheDay() throws Exception {
		clock.set(NOW);
		// 200 requests from 20 clients, so no client reaches its own limit.
		for (int i = 0; i < 200; i++) {
			requestCode("person" + i + "@example.com", "REGISTER", "198.51.100." + (i / 10)).andExpect(status().isNoContent());
		}

		requestCode("late@example.com", "REGISTER", "198.51.100.200")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

		assertThat(sentMail()).hasSize(200);
		assertThat(requestCount("ALL")).isEqualTo(200);
		// The day is the day in Sydney: a minute before its midnight the limit holds, and then a new count starts.
		clock.set(NEXT_SYDNEY_MIDNIGHT.minus(Duration.ofMinutes(1)));
		requestCode("late@example.com", "REGISTER", "198.51.100.200").andExpect(status().isTooManyRequests());
		clock.set(NEXT_SYDNEY_MIDNIGHT);
		requestCode("late@example.com", "REGISTER", "198.51.100.200").andExpect(status().isNoContent());
	}

	/** The count is one row that each request updates, so requests at the same moment cannot pass the limit together. */
	@Test
	void requestsAtTheSameMomentCannotPassTheLimitOfOneClientAddress() throws Exception {
		clock.set(NOW);
		for (int i = 1; i <= 8; i++) {
			requestCode("person" + i + "@example.com", "REGISTER", CLIENT).andExpect(status().isNoContent());
		}
		AtomicInteger next = new AtomicInteger(100);

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(6, () -> requestCode(
				"person" + next.incrementAndGet() + "@example.com", "REGISTER", CLIENT).andReturn().getResponse().getStatus());

		assertThat(outcomes).allMatch(RaceRunner.Outcome::succeeded);
		assertThat(outcomes).extracting(RaceRunner.Outcome::value).containsExactlyInAnyOrder(204, 204, 429, 429, 429, 429);
		assertThat(requestCount("IP:" + CLIENT)).isEqualTo(10);
		assertThat(sentMail()).hasSize(10);
	}

	@Test
	void requestsForOneAddressAtTheSameMomentGiveOneCode() throws Exception {
		clock.set(NOW);

		List<RaceRunner.Outcome<Integer>> outcomes = RaceRunner.run(4,
				() -> requestCode("new@example.com", "REGISTER").andReturn().getResponse().getStatus());

		assertThat(outcomes).extracting(RaceRunner.Outcome::value).containsExactlyInAnyOrder(204, 429, 429, 429);
		assertThat(sentMail()).hasSize(1);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isEqualTo(1);
	}

	@Test
	void aMalformedRequestIsRefused() throws Exception {
		requestCode("not-an-address", "REGISTER")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("email"));
		requestCode("new@example.com", "SOMETHING_ELSE")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(sentMail()).isEmpty();
		assertThat(requestCount("ALL")).isZero();
	}

}
