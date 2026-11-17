package com.opsflow.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * The login counts that belong to a client address, kept in Redis (Q-07; see Login security and account
 * recovery in docs/database/database-design.md): the failures of one account from one address, the lock of
 * that address out of the account, and the failures of one address on any username. {@link LoginLockPolicy}
 * says what the counts mean.
 *
 * <b>Each change is one atomic step in Redis.</b> A failure is counted by a script that also starts the count
 * again when the day or the window has changed, so two failures at the same moment get two different numbers
 * and exactly one of them reaches a step. A lock is written by a script that keeps the later end, so a short
 * lock that arrives late never shortens a longer one. The days, the windows, and the ends of the locks are
 * taken from the clock of the application, as everywhere else; the expiry of a key in Redis only clears it away.
 *
 * <b>When Redis cannot be reached, the login goes on without these limits</b>, and a warning is logged at most
 * once a minute. The ceiling on the failures of an account, which lives in the user row, still holds. Refusing
 * every login while Redis is down would lock everybody out to stop a stranger.
 */
@Component
public class LoginLimits {

	private static final Logger log = LoggerFactory.getLogger(LoginLimits.class);

	static final String PREFIX = "opsflow:login:";
	/** How long a count stays in Redis after its last change. The count itself is read with its day or window. */
	private static final Duration KEEP = Duration.ofDays(2);
	private static final Duration WARN_EVERY = Duration.ofMinutes(1);

	/**
	 * Adds one to the count in the hash KEYS[1] for the period ARGV[1] (a day or a window), starting again
	 * when the period has changed, and keeps the key for ARGV[2] milliseconds. Returns the new count.
	 */
	private static final RedisScript<Long> COUNT = RedisScript.of("""
			local n
			if redis.call('HGET', KEYS[1], 'period') == ARGV[1] then
			  n = redis.call('HINCRBY', KEYS[1], 'count', 1)
			else
			  redis.call('HSET', KEYS[1], 'period', ARGV[1], 'count', 1)
			  n = 1
			end
			redis.call('PEXPIRE', KEYS[1], ARGV[2])
			return n
			""", Long.class);

	/** Sets the lock KEYS[1] to end at ARGV[1] (epoch milliseconds) unless it already ends later. */
	private static final RedisScript<Long> LOCK = RedisScript.of("""
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			if tonumber(ARGV[1]) > current then
			  redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
			  return 1
			end
			return 0
			""", Long.class);

	/**
	 * A failure of an account at an address, as counted.
	 *
	 * @param failures the failures of the account from this address today, this one included
	 * @param lockedUntil when this failure locked the address out of the account, or null
	 */
	record AddressFailure(int failures, Instant lockedUntil) {
	}

	private final StringRedisTemplate redis;
	private final LoginLockPolicy policy;
	private final Clock clock;
	private final AtomicLong lastWarning = new AtomicLong();

	LoginLimits(StringRedisTemplate redis, LoginLockPolicy policy, Clock clock) {
		this.redis = redis;
		this.policy = policy;
		this.clock = clock;
	}

	/** When the address may try again, if it has had its failures in this window; empty otherwise. */
	Optional<Instant> addressRefusedUntil(String address) {
		return quietly(() -> {
			Instant now = clock.instant();
			List<Object> fields = redis.opsForHash().multiGet(addressKey(address), List.of("period", "count"));
			if (!String.valueOf(policy.addressWindow(now)).equals(fields.get(0)) || fields.get(1) == null) {
				return Optional.<Instant>empty();
			}
			return Integer.parseInt((String) fields.get(1)) >= policy.addressFailures() ? Optional.of(policy.addressWindowEnd(now))
					: Optional.<Instant>empty();
		}, Optional.empty());
	}

	/** When this address may try this account again, if it is locked out of it now; empty otherwise. */
	Optional<Instant> lockedUntil(long userId, String address) {
		return quietly(() -> {
			String value = redis.opsForValue().get(lockKey(userId, address));
			if (value == null) {
				return Optional.<Instant>empty();
			}
			Instant end = Instant.ofEpochMilli(Long.parseLong(value));
			return end.isAfter(clock.instant()) ? Optional.of(end) : Optional.<Instant>empty();
		}, Optional.empty());
	}

	/** Counts a failure of the address on any username: an unknown one, or a wrong password. */
	void recordAddressFailure(String address) {
		quietly(() -> redis.execute(COUNT, List.of(addressKey(address)), String.valueOf(policy.addressWindow(clock.instant())),
				String.valueOf(KEEP.toMillis())), null);
	}

	/**
	 * Counts a wrong password of the account at the address, and locks the address out of the account when the
	 * count reaches a step.
	 *
	 * @return the count and the lock, or empty when Redis could not be reached
	 */
	Optional<AddressFailure> recordFailure(long userId, String address) {
		return quietly(() -> {
			Instant now = clock.instant();
			Long failures = redis.execute(COUNT, List.of(countKey(userId, address)), policy.day(now).toString(),
					String.valueOf(KEEP.toMillis()));
			redis.opsForSet().add(addressesKey(userId), address);
			redis.expire(addressesKey(userId), KEEP);
			int count = failures == null ? 0 : failures.intValue();
			Instant lockedUntil = policy.addressLockAfter(count, now).orElse(null);
			if (lockedUntil != null) {
				long keep = Duration.between(now, lockedUntil).plus(KEEP).toMillis();
				redis.execute(LOCK, List.of(lockKey(userId, address)), String.valueOf(lockedUntil.toEpochMilli()), String.valueOf(keep));
			}
			return Optional.of(new AddressFailure(count, lockedUntil));
		}, Optional.empty());
	}

	/** A successful login clears the count and the lock of the account at this address only. */
	void clear(long userId, String address) {
		quietly(() -> redis.delete(List.of(countKey(userId, address), lockKey(userId, address))), null);
	}

	/** A password reset clears the counts and the locks of the account at every address. */
	void clearAccount(long userId) {
		quietly(() -> {
			Set<String> addresses = redis.opsForSet().members(addressesKey(userId));
			if (addresses != null) {
				for (String address : addresses) {
					redis.delete(List.of(countKey(userId, address), lockKey(userId, address)));
				}
			}
			return redis.delete(addressesKey(userId));
		}, null);
	}

	static String addressKey(String address) {
		return PREFIX + "address:" + address;
	}

	static String countKey(long userId, String address) {
		return PREFIX + "count:" + userId + ":" + address;
	}

	static String lockKey(long userId, String address) {
		return PREFIX + "lock:" + userId + ":" + address;
	}

	/** The addresses that have a count for the account, so that a reset finds them all. */
	static String addressesKey(long userId) {
		return PREFIX + "addresses:" + userId;
	}

	private <T> T quietly(Supplier<T> work, T whenUnreachable) {
		try {
			return work.get();
		}
		catch (RuntimeException e) {
			// Elapsed time only, so the monotonic clock and not the time of day.
			long now = System.nanoTime();
			long last = lastWarning.get();
			if ((last == 0 || now - last >= WARN_EVERY.toNanos()) && lastWarning.compareAndSet(last, now)) {
				log.warn("Redis cannot be reached, so logins go on without the limits of each address: {}", e.getClass().getName());
			}
			return whenUnreachable;
		}
	}

}
