package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.opsflow.common.config.JobsConfig;
import com.opsflow.common.config.OpsflowProperties;

/**
 * The cleanup job (T-49), called directly: the scheduled jobs are off in the tests. It deletes the codes
 * that have expired and the request counts of windows long over, and nothing that is still in use.
 */
class EmailCodeCleanupJobTest extends AuthIntegrationSupport {

	private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");

	@Autowired
	EmailCodeCleanupJob job;

	@Autowired
	EmailCodeRepository repository;

	@Autowired
	OpsflowProperties properties;

	@Autowired
	ApplicationContext context;

	@Test
	void anExpiredCodeIsDeletedAndAValidOneIsKept() throws Exception {
		clock.set(NOW);
		requestCode("old@example.com", "REGISTER").andExpect(status().isNoContent());
		clock.advance(Duration.ofMinutes(5));
		String code = codeFor("new@example.com", "REGISTER");

		// The first code expired a second ago, and the second one has 5 minutes left.
		clock.set(NOW.plus(Duration.ofMinutes(10)).plusSeconds(1));
		job.run();

		assertThat(root().queryForList("SELECT email FROM email_verification_codes", String.class)).containsExactly("new@example.com");
		signUp("newbie", "new@example.com", PASSWORD, code).andExpect(status().isCreated());
	}

	@Test
	void aCodeIsNotDeletedBeforeItExpires() throws Exception {
		clock.set(NOW);
		requestCode("new@example.com", "REGISTER").andExpect(status().isNoContent());

		clock.set(NOW.plus(Duration.ofMinutes(10)));
		job.run();

		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isEqualTo(1);
	}

	@Test
	void theCountsOfWindowsLongOverAreDeletedAndTheCurrentOnesAreKept() throws Exception {
		clock.set(NOW);
		requestCode("new@example.com", "REGISTER", "203.0.113.7").andExpect(status().isNoContent());
		root().update("INSERT INTO email_code_request_counts (scope, window_start, request_count) VALUES ('ALL', ?, 200), ('IP:203.0.113.7', ?, 10)",
				LocalDateTime.of(2026, 11, 10, 13, 0), LocalDateTime.of(2026, 11, 10, 22, 0));

		job.run();

		assertThat(root().queryForList("SELECT CONCAT(scope, ' ', request_count) FROM email_code_request_counts ORDER BY scope",
				String.class)).containsExactly("ALL 1", "IP:203.0.113.7 1");
	}

	/** A run deletes at most its batch and leaves the rest to the next run. */
	@Test
	void oneRunDeletesAtMostItsBatch() {
		clock.set(NOW);
		for (int i = 0; i < 5; i++) {
			root().update("INSERT INTO email_verification_codes (email, purpose, code_hash, expires_at) VALUES (?, 'REGISTER', 'x', ?)",
					"old" + i + "@example.com", LocalDateTime.of(2026, 11, 12, 0, 0));
		}
		OpsflowProperties.EmailCode configured = properties.emailCode();
		OpsflowProperties.EmailCode batchOfTwo = new OpsflowProperties.EmailCode(configured.ttl(), configured.maxWrongEntries(),
				configured.resendAfter(), configured.requestsPerIpPerHour(), configured.requestsPerDay(), configured.dayZone(),
				configured.cleanupInterval(), 2);
		EmailCodeCleanupJob small = new EmailCodeCleanupJob(repository, new OpsflowProperties(properties.retry(),
				properties.paging(), properties.login(), properties.token(), batchOfTwo, properties.mail(), properties.jobs(),
				properties.quantity(), properties.warehouse(), properties.order(), properties.timeFilter(), properties.ticket()), clock);

		small.run();
		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isEqualTo(3);
		small.run();
		small.run();
		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isZero();
	}

	@Test
	void runningTheJobTwiceChangesNothingMore() throws Exception {
		clock.set(NOW);
		requestCode("old@example.com", "REGISTER").andExpect(status().isNoContent());
		clock.advance(Duration.ofMinutes(11));

		job.run();
		job.run();

		assertThat(root().queryForObject("SELECT COUNT(*) FROM email_verification_codes", Integer.class)).isZero();
	}

	/** No job runs by itself in the tests, so no job changes rows in the middle of another test. */
	@Test
	void theScheduleIsOffInTheTests() {
		assertThat(context.getBeanNamesForType(JobsConfig.class)).isEmpty();
		assertThat(List.of(properties.jobs().enabled())).containsExactly(false);
	}

}
