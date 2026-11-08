package com.opsflow.common.config;

import java.time.Duration;
import java.util.List;

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
public record OpsflowProperties(@NotNull @Valid Retry retry, @NotNull @Valid Paging paging) {

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
