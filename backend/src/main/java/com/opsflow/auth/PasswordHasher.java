package com.opsflow.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hashes and checks passwords. BCrypt is slow on purpose, so this work never runs inside a transaction,
 * where it would hold a connection and row locks for tens of milliseconds: both methods refuse to run
 * when a transaction is active. See No slow work inside a transaction in docs/backend/backend-design.md.
 */
@Component
public class PasswordHasher {

	private final PasswordEncoder encoder;

	public PasswordHasher(PasswordEncoder encoder) {
		this.encoder = encoder;
	}

	public String hash(String password) {
		refuseInsideTransaction();
		return encoder.encode(password);
	}

	/** A stored value that is not a hash, such as the marker of the SYSTEM identity, matches no password. */
	public boolean matches(String password, String hash) {
		refuseInsideTransaction();
		try {
			return encoder.matches(password, hash);
		}
		catch (IllegalArgumentException notAHash) {
			return false;
		}
	}

	private static void refuseInsideTransaction() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("A password must be hashed or checked outside a transaction");
		}
	}

}
