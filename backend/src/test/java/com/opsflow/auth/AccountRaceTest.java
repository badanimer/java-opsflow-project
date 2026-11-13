package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The race of a code that stops being the latest one while it is used, against the MySQL container.
 *
 * Sign-up and the password reset check the code first, then hash the new password outside any
 * transaction, and only then open the transaction that uses the code up. A new code can be requested in
 * between, and only the latest code may work. These tests make that happen at exactly that point: the
 * real {@link PasswordHasher} is wrapped, and right after it has hashed the password the test requests a
 * new code. No timing is involved, so the order is the same on every run.
 */
class AccountRaceTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
	private static final String EMAIL = "alice@example.com";
	private static final String NEW_PASSWORD = "new-horse-2";

	@MockitoSpyBean
	PasswordHasher hasher;

	/** Lets 61 seconds pass and requests a new code, once, after the next password has been hashed. */
	private void aNewCodeIsRequestedAfterThePasswordIsHashed(String purpose) {
		doAnswer(invocation -> {
			Object hash = invocation.callRealMethod();
			clock.advance(Duration.ofSeconds(61));
			requestCode(EMAIL, purpose).andExpect(status().isNoContent());
			return hash;
		}).doCallRealMethod().when(hasher).hash(anyString());
	}

	@Test
	void aSignUpIsRefusedWhenANewerCodeWasRequestedWhileThePasswordWasHashed() throws Exception {
		clock.set(NOW);
		String code = codeFor(EMAIL, "REGISTER");
		aNewCodeIsRequestedAfterThePasswordIsHashed("REGISTER");

		signUp("alice", EMAIL, PASSWORD, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALID"));

		assertThat(root().queryForObject("SELECT COUNT(*) FROM users WHERE username = 'alice'", Integer.class)).isZero();
		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action = 'USER_REGISTERED'", Integer.class))
				.isZero();
		// The newer code is the one that works.
		signUp("alice", EMAIL, PASSWORD, codeMailedTo(EMAIL)).andExpect(status().isCreated());
	}

	@Test
	void aResetIsRefusedWhenANewerCodeWasRequestedWhileThePasswordWasHashed() throws Exception {
		clock.set(NOW);
		long alice = account("alice");
		String code = codeFor(EMAIL, "RESET_PASSWORD");
		aNewCodeIsRequestedAfterThePasswordIsHashed("RESET_PASSWORD");

		resetPasswordWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CODE_INVALID"));

		// Nothing was changed: the old password still works, and no reset is logged.
		assertThat(auditRows("PASSWORD_RESET", alice)).isZero();
		login("alice", PASSWORD).andExpect(status().isOk());
		resetPasswordWith(EMAIL, codeMailedTo(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());
		login("alice", NEW_PASSWORD).andExpect(status().isOk());
	}

}
