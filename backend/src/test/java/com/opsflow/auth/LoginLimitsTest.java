package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.opsflow.common.config.OpsflowProperties;

/**
 * When Redis cannot be reached, a login goes on without the limits of each address (see Login security and
 * account recovery in docs/database/database-design.md): nothing throws, and nothing is refused.
 */
class LoginLimitsTest {

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final LoginLimits limits = new LoginLimits(redis,
			new LoginLockPolicy(new OpsflowProperties.Login(Map.of(5, Duration.ofMinutes(1)), 20, ZoneId.of("Australia/Sydney"), 100, 50,
					Duration.ofMinutes(15))),
			Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), ZoneOffset.UTC));

	@Test
	@SuppressWarnings("unchecked")
	void withoutRedisNoAddressIsRefusedAndNoFailureThrows() {
		RedisConnectionFailureException down = new RedisConnectionFailureException("Connection refused");
		when(redis.opsForHash()).thenThrow(down);
		when(redis.opsForValue()).thenThrow(down);
		when(redis.opsForSet()).thenThrow(down);
		when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenThrow(down);
		when(redis.delete(anyList())).thenThrow(down);

		assertThat(limits.addressRefusedUntil("10.0.0.7")).isEmpty();
		assertThat(limits.lockedUntil(7, "10.0.0.7")).isEmpty();
		assertThat(limits.recordFailure(7, "10.0.0.7")).isEmpty();
		limits.recordAddressFailure("10.0.0.7");
		limits.clear(7, "10.0.0.7");
		limits.clearAccount(7);
	}

}
