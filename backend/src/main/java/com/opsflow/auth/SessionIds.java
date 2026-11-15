package com.opsflow.auth;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The id of one login: 16 random bytes as 32 hexadecimal characters, the size of {@code users.session_id}.
 * A login writes a new one, so the newest login of an account is the only one whose token is accepted
 * (see One signed-in device in docs/database/database-design.md). The id is random so that it says
 * nothing about the account or the time, and two logins never get the same one.
 */
final class SessionIds {

	private static final SecureRandom RANDOM = new SecureRandom();

	private SessionIds() {
	}

	static String newId() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

}
