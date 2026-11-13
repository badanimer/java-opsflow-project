package com.opsflow.auth;

import static com.opsflow.auth.EmailCodePurpose.REGISTER;
import static com.opsflow.auth.EmailCodePurpose.RESET_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The email codes and their stored value, without a database. */
class EmailCodeHasherTest {

	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

	private final EmailCodeHasher hasher = new EmailCodeHasher(KEY);

	@Test
	void aCodeHasSixDigits() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String code = hasher.newCode();
			assertThat(code).matches("\\d{6}");
			codes.add(code);
		}
		assertThat(codes).as("the codes differ").hasSizeGreaterThan(190);
	}

	@Test
	void aCodeMatchesOnlyItsOwnStoredValue() {
		String stored = hasher.hash(REGISTER, "alice@example.com", "123456");

		assertThat(stored).matches("[0-9a-f]{64}").doesNotContain("123456");
		assertThat(hasher.matches(REGISTER, "alice@example.com", "123456", stored)).isTrue();
		assertThat(hasher.matches(REGISTER, "alice@example.com", "123457", stored)).isFalse();
	}

	/** The purpose and the address are part of the stored value, and the case of the address is not. */
	@Test
	void aCodeWorksForItsPurposeAndAddressOnly() {
		String stored = hasher.hash(REGISTER, "alice@example.com", "123456");

		assertThat(hasher.matches(RESET_PASSWORD, "alice@example.com", "123456", stored)).isFalse();
		assertThat(hasher.matches(REGISTER, "bob@example.com", "123456", stored)).isFalse();
		assertThat(hasher.matches(REGISTER, "Alice@Example.COM", "123456", stored)).isTrue();
	}

	/** Without the key, the stored value says nothing about the code. */
	@Test
	void theStoredValueDependsOnTheKey() {
		String stored = hasher.hash(REGISTER, "alice@example.com", "123456");

		assertThat(new EmailCodeHasher(OTHER_KEY).matches(REGISTER, "alice@example.com", "123456", stored)).isFalse();
	}

	@Test
	void theMarkerOfACodeThatWasNotMailedMatchesNoEntry() {
		assertThat(hasher.matches(REGISTER, "alice@example.com", "123456", EmailCodeHasher.NOT_SENT)).isFalse();
		assertThat(hasher.matches(REGISTER, "alice@example.com", EmailCodeHasher.NOT_SENT, EmailCodeHasher.NOT_SENT)).isFalse();
	}

	@Test
	void aKeyThatIsNotBase64IsRefusedWithoutBeingQuoted() {
		assertThatThrownBy(() -> new EmailCodeHasher("not base64 !!")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("OPSFLOW_TOKEN_SIGNING_KEY").hasMessageNotContaining("not base64").hasNoCause();
	}

}
