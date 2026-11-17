package com.opsflow.common.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.config.OpsflowProperties;

import jakarta.annotation.PreDestroy;

/**
 * Sends mail (see Mail and No slow work inside a transaction in docs/backend/backend-design.md).
 *
 * <ul>
 * <li><b>Never inside a transaction.</b> A mail server is slow, and a mail cannot be undone by a rollback,
 * so {@link #send} refuses to run while a transaction is active. A caller commits first and sends
 * afterwards.</li>
 * <li><b>The caller does not wait.</b> The mail is handed to one worker thread and the caller returns at once.
 * The time of an answer therefore does not show whether a mail was sent, which matters where the answer
 * must be the same whether or not an email address is registered.</li>
 * <li><b>Through the queue.</b> The worker puts the mail in the {@link MailQueue}, which keeps it across a
 * restart, and a listener takes it out and gives it to {@link #deliverQueued}, which tries the mail server
 * three times. When the queue cannot be reached, the worker sends the mail itself, as before the queue.</li>
 * <li><b>A failure is logged and not reported.</b> The request has already been answered. The log line
 * carries no address and no text of the mail. The person asks for a new mail.</li>
 * </ul>
 *
 * The local hand-over is short on purpose: more waiting mails than that mean something is wrong, and the
 * mails beyond it are dropped with a log line.
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);
	private static final int QUEUE = 200;
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);
	/** The waits before the second and the third try of the mail server, for a mail from the queue. */
	private static final Duration[] RETRY_DELAYS = { Duration.ofSeconds(2), Duration.ofSeconds(4) };

	private final MailTransport transport;
	private final MailQueue queue;
	private final Duration queueTtl;
	/** The mails handed over by this instance and not yet sent or given up, by id, with the time of the hand-over. */
	private final Map<String, Instant> pending = new ConcurrentHashMap<>();
	/** One thread, so the mails reach the queue in the order they were handed over. */
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE),
			runnable -> {
				Thread thread = new Thread(runnable, "mailer");
				thread.setDaemon(true);
				return thread;
			});
	private final Duration[] retryDelays;

	@Autowired
	public Mailer(MailTransport transport, MailQueue queue, OpsflowProperties properties) {
		this(transport, queue, properties.mail().queueTtl(), RETRY_DELAYS);
	}

	Mailer(MailTransport transport, MailQueue queue, Duration queueTtl, Duration... retryDelays) {
		this.transport = transport;
		this.queue = queue;
		this.queueTtl = queueTtl;
		this.retryDelays = retryDelays;
	}

	public void send(OutgoingMail mail) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("A mail must be sent after the transaction has committed, not inside it");
		}
		String id = UUID.randomUUID().toString();
		pending.put(id, Instant.now());
		try {
			worker.execute(() -> handOver(id, mail));
		}
		catch (RejectedExecutionException full) {
			pending.remove(id);
			log.error("Mail dropped, the queue of {} waiting mails is full: {}", QUEUE, mail.subject());
		}
	}

	/**
	 * A mail from the queue: tries the mail server up to three times. When every try fails, the mail goes to the
	 * dead letters, and the next mail is taken.
	 */
	public void deliverQueued(String id, OutgoingMail mail) {
		try {
			for (int attempt = 0;; attempt++) {
				try {
					transport.send(mail);
					return;
				}
				catch (RuntimeException e) {
					if (attempt >= retryDelays.length) {
						// The message of a mail failure can quote the address, so only its type is logged.
						log.error("Mail not sent after {} tries, kept as a dead letter ({}): {}", attempt + 1, mail.subject(), e.getClass().getName());
						throw new AmqpRejectAndDontRequeueException("The mail server refused the mail");
					}
					sleep(retryDelays[attempt]);
				}
			}
		}
		finally {
			if (id != null) {
				pending.remove(id);
			}
		}
	}

	/**
	 * Waits until every mail this instance handed over has been sent or given up, or the time is up. Used by the
	 * tests that look at what was sent. A mail that has waited longer than the queue keeps it has expired there,
	 * and is no longer waited for.
	 *
	 * @return false when the time was up first
	 */
	public boolean flush(Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		if (!handedOver(timeout)) {
			return false;
		}
		while (true) {
			Instant expired = Instant.now().minus(queueTtl);
			pending.values().removeIf(at -> at.isBefore(expired));
			if (pending.isEmpty()) {
				return true;
			}
			if (System.nanoTime() > deadline) {
				return false;
			}
			sleep(Duration.ofMillis(20));
		}
	}

	/** Waits until the worker has handed over every mail so far, to the queue or to the mail server. */
	private boolean handedOver(Duration timeout) {
		Future<?> marker;
		try {
			marker = worker.submit(() -> {
			});
		}
		catch (RejectedExecutionException stoppedOrFull) {
			return false;
		}
		try {
			marker.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			return true;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (ExecutionException | TimeoutException e) {
			return false;
		}
	}

	/** The mails in the queue outlive the application, so only the local hand-over is waited for. */
	@PreDestroy
	void stop() {
		if (!handedOver(SHUTDOWN_WAIT)) {
			log.warn("The application stopped before every waiting mail was handed over");
		}
		worker.shutdownNow();
	}

	private void handOver(String id, OutgoingMail mail) {
		try {
			queue.publish(id, mail);
			return;
		}
		catch (RuntimeException unreachable) {
			log.warn("The mail queue cannot be reached, so the mail is sent at once ({}): {}", mail.subject(),
					unreachable.getClass().getName());
		}
		try {
			transport.send(mail);
		}
		catch (RuntimeException e) {
			log.error("Mail not sent ({}): {}", mail.subject(), e.getClass().getName());
		}
		finally {
			pending.remove(id);
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
