package com.opsflow.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The clock of the application in the integration tests. It shows the real time until a test sets it;
 * from then on it stands still and moves only when the test moves it, so a test can let a minute or a
 * night pass without waiting.
 */
public final class MutableClock extends Clock {

	/** Replaces the clock of the application with this one. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}
	}

	private volatile Instant fixed;

	/** Stops the clock at this instant. */
	public void set(Instant instant) {
		fixed = instant;
	}

	/** Stops the clock, if it is not stopped yet, and moves it forward. */
	public void advance(Duration duration) {
		fixed = instant().plus(duration);
	}

	public void useSystemTime() {
		fixed = null;
	}

	@Override
	public Instant instant() {
		Instant stopped = fixed;
		return stopped != null ? stopped : Instant.now();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("The application reads time in UTC only");
	}

}
