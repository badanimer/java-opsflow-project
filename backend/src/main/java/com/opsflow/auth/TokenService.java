package com.opsflow.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.opsflow.common.config.OpsflowProperties;

/**
 * Issues and reads the login token: a JWT signed with HMAC-SHA256.
 *
 * The token carries only the id of the user ({@code sub}), the time it was issued, and the id of the login
 * it belongs to ({@value #SESSION_ID}), which the token check compares with {@code users.session_id} so
 * that only the newest login of an account is signed in. Nothing else about the user is in it, because
 * every request loads the user from the database (see Token checks in docs/database/database-design.md). The issue time is also stored in milliseconds
 * ({@value #ISSUED_AT_MILLIS}), because the standard {@code iat} is in whole seconds and
 * {@code users.password_changed_at} is not: with seconds alone, a login in the same second as a password
 * reset could not be told from one before it.
 *
 * The signing key is a secret from the environment, Base64 of at least 32 bytes. It is never logged.
 */
@Component
public class TokenService {

	static final String ISSUED_AT_MILLIS = "iat_ms";

	static final String SESSION_ID = "sid";

	private static final int MIN_KEY_BYTES = 32;

	/** A token and the moment it stops being valid. */
	public record IssuedToken(String value, Instant expiresAt) {
	}

	private final Duration ttl;
	private final Clock clock;
	private final JwtEncoder encoder;
	private final JwtDecoder decoder;

	@Autowired
	public TokenService(@Value("${opsflow.token-signing-key}") String signingKey, OpsflowProperties properties, Clock clock) {
		this(signingKey, properties.token().ttl(), clock);
	}

	TokenService(String signingKey, Duration ttl, Clock clock) {
		SecretKey key = keyOf(signingKey);
		this.ttl = ttl;
		this.clock = clock;
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		nimbus.setJwtValidator(this::validate);
		this.decoder = nimbus;
	}

	/** @param sessionId the id that the login wrote to {@code users.session_id} in its transaction */
	public IssuedToken issue(long userId, Instant issuedAt, String sessionId) {
		Instant expiresAt = issuedAt.plus(ttl);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(Long.toString(userId))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim(ISSUED_AT_MILLIS, issuedAt.toEpochMilli())
				.claim(SESSION_ID, sessionId)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		// The token holds its expiry in whole seconds, so that is the time reported to the client.
		return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
				expiresAt.truncatedTo(ChronoUnit.SECONDS));
	}

	/**
	 * Checks the signature and the expiry. Whether the account may still use the token is checked by
	 * {@link TokenAuthenticator}.
	 */
	JwtDecoder decoder() {
		return decoder;
	}

	/** The id of the user the token was issued to, or null when the token does not name one. */
	static Long userId(Jwt jwt) {
		try {
			return Long.valueOf(jwt.getSubject());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** The time the token was issued, to the millisecond, or null when the token does not carry it. */
	static Instant issuedAt(Jwt jwt) {
		return jwt.getClaim(ISSUED_AT_MILLIS) instanceof Number millis ? Instant.ofEpochMilli(millis.longValue()) : null;
	}

	/** The id of the login the token belongs to, or null when the token does not carry one. */
	static String sessionId(Jwt jwt) {
		return jwt.getClaim(SESSION_ID) instanceof String id ? id : null;
	}

	/** The expiry is read on the clock of the application, the same clock that issued the token. */
	private OAuth2TokenValidatorResult validate(Jwt jwt) {
		Instant expiresAt = jwt.getExpiresAt();
		if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The token has expired", null));
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static SecretKey keyOf(String signingKey) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(signingKey.trim());
		}
		catch (IllegalArgumentException e) {
			// The message of the cause may quote the key, so the cause is not passed on.
			throw new IllegalStateException("The token signing key (OPSFLOW_TOKEN_SIGNING_KEY) is not valid Base64");
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException(
					"The token signing key (OPSFLOW_TOKEN_SIGNING_KEY) must be at least 32 bytes, encoded in Base64");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
