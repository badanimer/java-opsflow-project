package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Password hashing, without a database. */
class PasswordHasherTest {

	private final PasswordHasher hasher = new PasswordHasher(PasswordEncoderFactories.createDelegatingPasswordEncoder());

	@AfterEach
	void noTransaction() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void aPasswordMatchesItsOwnHashAndNoOther() {
		String hash = hasher.hash("correct-horse-1");

		assertThat(hash).startsWith("{bcrypt}").doesNotContain("correct-horse-1");
		assertThat(hasher.matches("correct-horse-1", hash)).isTrue();
		assertThat(hasher.matches("wrong-horse-1", hash)).isFalse();
		assertThat(hasher.hash("correct-horse-1")).as("each hash has its own salt").isNotEqualTo(hash);
	}

	@Test
	void aStoredValueThatIsNotAHashMatchesNoPassword() {
		assertThat(hasher.matches("!SYSTEM-CANNOT-LOG-IN", "!SYSTEM-CANNOT-LOG-IN")).isFalse();
		assertThat(hasher.matches("x", "x")).isFalse();
	}

	@Test
	void hashingOrCheckingInsideATransactionIsRefused() {
		String hash = hasher.hash("correct-horse-1");
		TransactionSynchronizationManager.setActualTransactionActive(true);

		assertThatThrownBy(() -> hasher.hash("correct-horse-1")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("outside a transaction");
		assertThatThrownBy(() -> hasher.matches("correct-horse-1", hash)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("outside a transaction");
	}

}
