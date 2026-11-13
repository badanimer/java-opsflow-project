package com.opsflow.common.mail;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

/**
 * Sends mail (see No slow work inside a transaction in docs/backend/backend-design.md).
 *
 * <ul>
 * <li><b>Never inside a transaction.</b> A mail server is slow, and a mail cannot be undone by a rollback,
 * so {@link #send} refuses to run while a transaction is active. A caller commits first and sends
 * afterwards.</li>
 * <li><b>In the background.</b> The mail is handed to one worker thread and the caller returns at once.
 * The time of an answer therefore does not show whether a mail was sent, which matters where the answer
 * must be the same whether or not an email address is registered, and a slow or failing mail server does
 * not hold a request.</li>
 * <li><b>A failure is logged and not reported.</b> The request has already been answered. The log line
 * carries no address and no text of the mail. The person asks for a new mail.</li>
 * </ul>
 *
 * The queue is short on purpose: more waiting mails than that mean something is wrong, and the mails
 * beyond it are dropped with a log line.
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);
	private static final int QUEUE = 200;
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

	private final MailTransport transport;
	/** One thread, so the mails leave in the order they were handed over. */
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE),
			runnable -> {
				Thread thread = new Thread(runnable, "mailer");
				thread.setDaemon(true);
				return thread;
			});

	public Mailer(MailTransport transport) {
		this.transport = transport;
	}

	public void send(OutgoingMail mail) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("A mail must be sent after the transaction has committed, not inside it");
		}
		try {
			worker.execute(() -> deliver(mail));
		}
		catch (RejectedExecutionException full) {
			log.error("Mail dropped, the queue of {} waiting mails is full: {}", QUEUE, mail.subject());
		}
	}

	/**
	 * Waits until every mail handed over so far has been given to the mail server, or the time is up.
	 * Used when the application stops, and by tests that look at what was sent.
	 *
	 * @return false when the time was up first
	 */
	public boolean flush(Duration timeout) {
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

	@PreDestroy
	void stop() {
		if (!flush(SHUTDOWN_WAIT)) {
			log.warn("The application stopped before every waiting mail was sent");
		}
		worker.shutdownNow();
	}

	private void deliver(OutgoingMail mail) {
		try {
			transport.send(mail);
		}
		catch (RuntimeException e) {
			// The message of a mail failure can quote the address, so only its type is logged.
			log.error("Mail not sent ({}): {}", mail.subject(), e.getClass().getName());
		}
	}

}
