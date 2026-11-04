package com.opsflow.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Starts the same task on several threads at the same moment, for race tests.
 * Every thread waits at a latch until all of them are ready, so the calls overlap as much as possible.
 */
public final class RaceRunner {

	private static final long TIMEOUT_SECONDS = 60;

	private RaceRunner() {
	}

	/** The result of one thread: its value, or the exception it ended with. */
	public record Outcome<T>(T value, Throwable error) {

		public boolean succeeded() {
			return error == null;
		}
	}

	public static <T> List<Outcome<T>> run(int threads, Callable<T> task) {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Outcome<T>>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					try {
						return new Outcome<>(task.call(), null);
					}
					catch (Throwable e) {
						return new Outcome<T>(null, e);
					}
				}));
			}
			if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("The threads did not all get ready");
			}
			start.countDown();
			List<Outcome<T>> outcomes = new ArrayList<>();
			for (Future<Outcome<T>> future : futures) {
				outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
			}
			return outcomes;
		}
		catch (Exception e) {
			throw new IllegalStateException("The race did not finish", e);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
