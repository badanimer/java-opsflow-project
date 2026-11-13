package com.opsflow.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduled jobs on (see Scheduled Jobs in docs/backend/backend-design.md). A job is a method
 * with {@code @Scheduled} in its module. With {@code opsflow.jobs.enabled} set to false no job runs by
 * itself: the tests do that, and call a job directly when they test it, so no job changes rows in the
 * middle of another test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "opsflow.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class JobsConfig {
}
