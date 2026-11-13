package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/** The rules for a new username and a new password, without a database. */
class AccountRulesTest {

	@ParameterizedTest
	@ValueSource(strings = { "abcdefg1", "correct-horse-1", "A1 b c d e", "a23456789012345678901234","pässwörd1" })
	void aPasswordOfEightToTwentyFourCharactersWithALetterAndADigitIsAccepted(String password) {
		assertThatCode(() -> AccountRules.checkPassword("password", password)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "abcdef1", "abcdefgh", "12345678", "1234567890123456789012345a", "--------" })
	void aPasswordThatIsTooShortTooLongOrLacksALetterOrADigitIsRefused(String password) {
		assertThatThrownBy(() -> AccountRules.checkPassword("newPassword", password)).isInstanceOfSatisfying(ApiException.class,
				refusal -> {
					assertThat(refusal.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					// The message names the rule and never repeats the password.
					assertThat(refusal.extras().toString()).contains("newPassword").doesNotContain(password);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = { "bob", "alice", "AliceSmith", "opsUser7", "1234" })
	void aUsernameOfLettersAndDigitsIsAccepted(String username) {
		assertThatCode(() -> AccountRules.checkUsername("username", username)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "al", "alice smith", "alice@home", " alice", "älice", "a/b/c", "Alice.Smith", "ops_user", "ops-user" })
	void aUsernameThatIsTooShortOrHasOtherCharactersIsRefused(String username) {
		assertThatThrownBy(() -> AccountRules.checkUsername("username", username)).isInstanceOfSatisfying(ApiException.class,
				refusal -> assertThat(refusal.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void aUsernameOfSixtyFiveCharactersIsRefused() {
		assertThatCode(() -> AccountRules.checkUsername("username", "a".repeat(64))).doesNotThrowAnyException();
		assertThatThrownBy(() -> AccountRules.checkUsername("username", "a".repeat(65))).isInstanceOf(ApiException.class);
	}

}
