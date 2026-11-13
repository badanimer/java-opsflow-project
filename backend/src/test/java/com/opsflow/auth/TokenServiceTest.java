package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.opsflow.auth.TokenService.IssuedToken;

/** The login token, without a database: what it carries, how long it lasts, and the signing key. */
class TokenServiceTest {

	private static final Instant ISSUED = Instant.parse("2026-11-13T01:00:00.250Z");
	private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
	private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

	private static TokenService service(String key, Instant now) {
		return new TokenService(key, Duration.ofHours(12), Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void theTokenCarriesOnlyTheUserIdAndItsIssueTimeToTheMillisecond() {
		TokenService tokens = service(KEY, ISSUED);

		IssuedToken token = tokens.issue(42, ISSUED);
		Jwt jwt = tokens.decoder().decode(token.value());

		assertThat(TokenService.userId(jwt)).isEqualTo(42L);
		assertThat(TokenService.issuedAt(jwt)).isEqualTo(ISSUED);
		assertThat(jwt.getClaims()).containsOnlyKeys("sub", "iat", "exp", "iat_ms");
		assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-13T13:00:00Z"));
		assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
	}

	@Test
	void theTokenIsRefusedFromTheMomentItExpires() {
		String token = service(KEY, ISSUED).issue(42, ISSUED).value();
		Instant expiry = Instant.parse("2026-11-13T13:00:00Z");

		assertThat(service(KEY, expiry.minusSeconds(1)).decoder().decode(token)).isNotNull();
		assertThatThrownBy(() -> service(KEY, expiry).decoder().decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void aTokenSignedWithAnotherKeyOrChangedAfterwardsIsRefused() {
		String token = service(KEY, ISSUED).issue(42, ISSUED).value();
		String[] parts = token.split("\\.");
		String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"1\",\"iat_ms\":1,\"exp\":99999999999}".getBytes()) + "." + parts[2];

		assertThatThrownBy(() -> service(OTHER_KEY, ISSUED).decoder().decode(token)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> service(KEY, ISSUED).decoder().decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void aSigningKeyThatIsTooShortOrNotBase64StopsTheStartAndIsNotQuoted() {
		String tooShort = Base64.getEncoder().encodeToString("only-16-bytes!!!".getBytes());

		assertThatThrownBy(() -> service(tooShort, ISSUED)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32 bytes").hasMessageNotContaining(tooShort);
		assertThatThrownBy(() -> service("not base64 at all !!", ISSUED)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Base64").hasMessageNotContaining("not base64 at all").hasNoCause();
	}

}
