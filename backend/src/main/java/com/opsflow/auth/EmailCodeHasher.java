package com.opsflow.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Makes email codes and the value that is stored for one.
 *
 * A code has 6 digits, so a plain hash of it could be reversed by trying the million codes, and BCrypt
 * would only slow that down. The stored value is therefore an HMAC-SHA256 with a key that is not in the
 * database: whoever reads {@code email_verification_codes} cannot work out a code without the key. It is
 * also fast, so a code can be checked without the rules for slow work.
 *
 * The key is the token signing key (OPSFLOW_TOKEN_SIGNING_KEY), so there is no second secret to keep.
 * The text that is signed starts with a line that no login token can start with, so a stored value can
 * never serve as the signature of a token. {@link TokenService} refuses a key that is too short.
 */
@Component
public class EmailCodeHasher {

	/** Stored when no mail was sent. It is not the HMAC of any code, so no entry matches it. */
	static final String NOT_SENT = "!NOT-SENT";

	private static final String ALGORITHM = "HmacSHA256";
	private static final String DOMAIN = "opsflow-email-code";

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public EmailCodeHasher(@Value("${opsflow.token-signing-key}") String signingKey) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(signingKey.trim());
		}
		catch (IllegalArgumentException e) {
			// The message of the cause may quote the key, so the cause is not passed on.
			throw new IllegalStateException("The token signing key (OPSFLOW_TOKEN_SIGNING_KEY) is not valid Base64");
		}
		this.key = new SecretKeySpec(bytes, ALGORITHM);
	}

	/** A new code: 6 digits, each as likely as the others, leading zeros included. */
	String newCode() {
		return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
	}

	/** The value stored for a code. The purpose and the address are part of it, so a code works for them only. */
	String hash(EmailCodePurpose purpose, String email, String code) {
		String text = DOMAIN + "\n" + purpose.name() + "\n" + email.toLowerCase(Locale.ROOT) + "\n" + code;
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC-SHA256 is not available", e);
		}
	}

	/** Compares in constant time, so the time of the answer does not show how much of a code was right. */
	boolean matches(EmailCodePurpose purpose, String email, String code, String storedHash) {
		return MessageDigest.isEqual(hash(purpose, email, code).getBytes(StandardCharsets.US_ASCII),
				storedHash.getBytes(StandardCharsets.US_ASCII));
	}

}
