package com.opsflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

class IdempotencyKeyTest {

	@Test
	void noHeaderMeansNoKey() {
		assertThat(IdempotencyKey.parse(null)).isEmpty();
	}

	@Test
	void aUuidAndOtherVisibleKeysAreAccepted() {
		String uuid = UUID.randomUUID().toString();

		assertThat(IdempotencyKey.parse(uuid)).contains(uuid);
		assertThat(IdempotencyKey.parse("order-2026/11:04_a.b~c")).isPresent();
		assertThat(IdempotencyKey.parse("x".repeat(128))).isPresent();
	}

	@Test
	void anEmptyOrTooLongKeyIsRefused() {
		assertRefused("");
		assertRefused("x".repeat(129));
	}

	@Test
	void spacesControlCharactersAndNonAsciiAreRefused() {
		assertRefused("two words");
		assertRefused("tab\there");
		assertRefused("line\nbreak");
		assertRefused("café");
	}

	private static void assertRefused(String value) {
		assertThatThrownBy(() -> IdempotencyKey.parse(value)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

}
