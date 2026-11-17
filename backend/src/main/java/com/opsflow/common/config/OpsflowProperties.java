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
		@NotNull @Valid Token token, @NotNull @Valid EmailCode emailCode, @NotNull @Valid Mail mail, @NotNull @Valid Jobs jobs,
		@NotNull @Valid Quantity quantity, @NotNull @Valid Warehouse warehouse, @NotNull @Valid Order order,
		@NotNull @Valid TimeFilter timeFilter, @NotNull @Valid Ticket ticket) {

	/**
	 * The ticket module (T-11, T-12, T-33, T-35).
	 *
	 * @param numberAttempts how many generated ticket numbers are tried before opening a ticket fails on a clash
	 * @param maxOpenCreated the most {@code OPEN} tickets one person may have created
	 * @param maxInProgress the most {@code IN_PROGRESS} tickets one person may hold
	 * @param maxItems the most warehouse items one ticket may link
	 * @param maxArchiveBatch the most tickets one batch archive may name
	 * @param autoArchiveAfter how long after {@code resolved_at} the archive job archives a resolved ticket
	 * @param autoArchiveAtResolved the number of {@code RESOLVED} tickets in the whole system at which the job archives all of them
	 * @param archiveInterval how often the archive job runs (read by its schedule)
	 * @param archiveJobBatch the most tickets one run of the archive job archives
	 */
	public record Ticket(@Min(1) int numberAttempts, @Min(1) int maxOpenCreated, @Min(1) int maxInProgress, @Min(1) int maxItems,
			@Min(1) int maxArchiveBatch, @NotNull Duration autoArchiveAfter, @Min(1) int autoArchiveAtResolved,
			@NotNull Duration archiveInterval, @Min(1) int archiveJobBatch) {
	}

	/**
	 * The time filter of the logs and the other lists (see The time filter).
	 *
	 * @param zone the display time zone, in which the days a person picks begin and end
	 */
	public record TimeFilter(@NotNull ZoneId zone) {
	}

	/**
	 * Placing an order (T-10, T-12).
	 *
	 * @param maxLines the most product lines one order may hold; the product page sends one
	 * @param numberAttempts how many generated order numbers are tried before an order fails on a clash
	 */
	public record Order(@Min(1) int maxLines, @Min(1) int numberAttempts) {
	}

	/**
	 * The ranges of Valid quantities (T-41).
	 *
	 * @param maxStockInput the most one stock input may be: a starting quantity, a stock-in, a write-off, or a correction
	 * @param maxOrderLine the most one order line may ask for
	 */
	public record Quantity(@Min(1) int maxStockInput, @Min(1) int maxOrderLine) {
	}

	/**
	 * Warehouse items and their images (T-39, T-42).
	 *
	 * @param imageDir the folder, outside the web root, where the item images are kept
	 * @param imageMaxBytes the largest image accepted
	 * @param skuAttempts how many generated SKUs are tried before adding an item fails
	 */
	public record Warehouse(@NotEmpty String imageDir, @Min(1) long imageMaxBytes, @Min(1) int skuAttempts) {
	}


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
	 * @param queueTtl how long a mail waits in the mail queue before it expires there; a code it holds is of no use later
	 */
	public record Mail(String host, @Min(1) int port, String username, String password, String from, @NotNull Duration queueTtl) {

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
	 * The lockout after wrong passwords (T-28, Q-07).
	 *
	 * @param lockSteps the failures of one account from one client address in a day that lock that address out
	 *     of the account, and for how long
	 * @param lockUntilMidnightAt the failures of one account from one address from which that address is locked
	 *     out until midnight
	 * @param resetZone the time zone of the midnight at which the counts of failures start again
	 * @param accountFailuresPerDay the failures of one account from every address in a day that lock the account
	 *     itself until midnight
	 * @param addressFailures the failures of one client address, on any username, in one window, after which the
	 *     address is refused until the window ends
	 * @param addressWindow the window of {@code addressFailures}
	 */
	public record Login(@NotEmpty Map<@Min(1) Integer, @NotNull Duration> lockSteps, @Min(1) int lockUntilMidnightAt,
			@NotNull ZoneId resetZone, @Min(1) int accountFailuresPerDay, @Min(1) int addressFailures, @NotNull Duration addressWindow) {
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

	/**
	 * @param pageSize the size of a page, which is also the most a client may ask for
	 * @param productPageSize the size of a page of the product page when the page asks for none
	 * @param productPageMax the most cards a page of the product page may ask for; the person chooses 8, 16, or 24
	 */
	public record Paging(@Min(1) int pageSize, @Min(1) int productPageSize, @Min(1) int productPageMax) {
	}

}
