package com.opsflow.common.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * The operating parameters (T-11). They live in application configuration under {@code opsflow.*}, not
 * in the database; the values are in application.yml and the reasons are in Operating Parameters in
 * docs/database/database-design.md. A module that needs another parameter adds its group here.
 */
@Validated
@ConfigurationProperties("opsflow")
public record OpsflowProperties(@NotNull @Valid Retry retry, @NotNull @Valid Paging paging, @NotNull @Valid Login login,
		@NotNull @Valid Token token, @NotNull @Valid EmailCode emailCode, @NotNull @Valid Mail mail, @NotNull @Valid Jobs jobs) {

	/**
	 * The email codes of sign-up and password recovery (T-29), and the limits on requesting them.
	 *
	 * @param ttl how long a code is valid
	 * @param maxWrongEntries the wrong entries after which a code is dead
	 * @param resendAfter the wait before a new code for the same email and purpose
	 * @param requestsPerIpPerHour the accepted requests of one client address in one hour
	 * @param requestsPerDay the accepted requests of the whole system in one day
	 * @param dayZone the time zone of the midnight at which that day starts
	 * @param cleanupInterval how often the cleanup job runs (read by its schedule)
	 * @param cleanupBatch the most rows one run of the cleanup deletes from each table
	 */
	public record EmailCode(@NotNull Duration ttl, @Min(1) int maxWrongEntries, @NotNull Duration resendAfter,
			@Min(1) int requestsPerIpPerHour, @Min(1) int requestsPerDay, @NotNull ZoneId dayZone,
			@NotNull Duration cleanupInterval, @Min(1) int cleanupBatch) {
	}

	/**
	 * The mail server. Without a host or without a password no mail is sent.
	 *
	 * @param from the sender address; the username when empty
	 */
	public record Mail(String host, @Min(1) int port, String username, String password, String from) {

		public boolean configured() {
			return host != null && !host.isBlank() && password != null && !password.isBlank();
		}

		public String sender() {
			return from != null && !from.isBlank() ? from : username;
		}

		/** The password is a secret, so it is never part of the text of this record. */
		@Override
		public String toString() {
			return "Mail[host=" + host + ", port=" + port + ", configured=" + configured() + "]";
		}
	}

	/** @param enabled whether the scheduled jobs run */
	public record Jobs(boolean enabled) {
	}

	/**
	 * The lockout after wrong passwords (T-28).
	 *
	 * @param lockSteps the number of failures in a row that locks the account, and for how long
	 * @param lockUntilMidnightAt the number of failures in a row from which the account is locked until midnight
	 * @param resetZone the time zone of the midnight at which the count of failures starts again
	 */
	public record Login(@NotEmpty Map<@Min(1) Integer, @NotNull Duration> lockSteps, @Min(1) int lockUntilMidnightAt,
			@NotNull ZoneId resetZone) {
	}

	/** @param ttl how long a login token is valid after it was issued */
	public record Token(@NotNull Duration ttl) {
	}

	/**
	 * The retry of a transaction that lost a deadlock or timed out on a lock.
	 *
	 * @param attempts the number of tries in total, the first one included
	 * @param delays the wait before the second try, before the third, and so on; the last one is reused
	 *     when there are more retries than delays
	 */
	public record Retry(@Min(1) int attempts, @NotEmpty List<@NotNull Duration> delays) {
	}

	/** @param pageSize the size of a page, which is also the most a client may ask for */
	public record Paging(@Min(1) int pageSize) {
	}

}
