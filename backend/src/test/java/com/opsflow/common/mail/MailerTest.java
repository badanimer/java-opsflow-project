package com.opsflow.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.config.OpsflowProperties;

/**
 * The mailer, with a mail server and a mail queue made by the test, and no broker. The queue of a test hands
 * each mail straight back to {@link Mailer#deliverQueued}, as the listener does, or fails like a broker that
 * cannot be reached.
 */
class MailerTest {

	private static final Duration WAIT = Duration.ofSeconds(10);
	private static final Duration TTL = Duration.ofMinutes(10);

	private final List<String> delivered = new CopyOnWriteArrayList<>();
	private final List<String> queued = new CopyOnWriteArrayList<>();
	private Mailer mailer;

	@AfterEach
	void stop() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
		if (mailer != null) {
			mailer.stop();
		}
	}

	private static OutgoingMail mail(String subject) {
		return new OutgoingMail("alice@example.com", subject, "The code is 123456.");
	}

	/** A mailer whose queue gives each mail to the listener side at once, with no wait between the tries. */
	private Mailer throughTheQueue(MailTransport server) {
		Mailer[] self = new Mailer[1];
		self[0] = new Mailer(server, (id, mail) -> {
			queued.add(mail.subject());
			try {
				self[0].deliverQueued(id, mail);
			}
			catch (AmqpRejectAndDontRequeueException deadLetter) {
				// The broker would keep it as a dead letter.
			}
		}, TTL, Duration.ZERO, Duration.ZERO);
		return self[0];
	}

	/** A mailer whose queue cannot be reached. */
	private Mailer withoutTheQueue(MailTransport server) {
		return new Mailer(server, (id, mail) -> {
			throw new AmqpConnectException(new java.net.ConnectException("Connection refused"));
		}, TTL);
	}

	@Test
	void mailsLeaveInTheOrderTheyWereHandedOver() {
		mailer = throughTheQueue(mail -> delivered.add(mail.subject()));

		for (int i = 1; i <= 20; i++) {
			mailer.send(mail("mail " + i));
		}

		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(queued).hasSize(20);
		assertThat(delivered).hasSize(20).startsWith("mail 1", "mail 2", "mail 3").endsWith("mail 20");
	}

	/** A mail server is slow and a mail cannot be rolled back, so a mail is sent after the commit. */
	@Test
	void sendingInsideATransactionIsRefused() {
		mailer = throughTheQueue(mail -> delivered.add(mail.subject()));
		TransactionSynchronizationManager.setActualTransactionActive(true);

		assertThatThrownBy(() -> mailer.send(mail("inside"))).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("after the transaction has committed");

		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).isEmpty();
	}

	/** The caller returns while the mail server is still busy, so the answer does not wait for it. */
	@Test
	void theCallerDoesNotWaitForTheMailServer() {
		CountDownLatch serverMayAnswer = new CountDownLatch(1);
		mailer = throughTheQueue(mail -> {
			try {
				serverMayAnswer.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			delivered.add(mail.subject());
		});

		mailer.send(mail("slow"));

		// send has returned, and the mail server has not answered yet.
		assertThat(delivered).isEmpty();
		serverMayAnswer.countDown();
		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).containsExactly("slow");
	}

	/** A mail from the queue gets three tries; a server that fails once more than that sends it to the dead letters. */
	@Test
	void aMailFromTheQueueIsTriedThreeTimesAndThenGivenUp() {
		AtomicInteger tries = new AtomicInteger();
		mailer = throughTheQueue(mail -> {
			if (mail.subject().equals("fails twice") && tries.incrementAndGet() <= 2) {
				throw new IllegalStateException("421 try again later");
			}
			if (mail.subject().equals("always fails")) {
				throw new IllegalStateException("535 authentication failed for alice@example.com");
			}
			delivered.add(mail.subject());
		});

		assertThatCode(() -> mailer.send(mail("fails twice"))).doesNotThrowAnyException();
		mailer.send(mail("always fails"));
		mailer.send(mail("works"));

		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).containsExactly("fails twice", "works");
		assertThatThrownBy(() -> mailer.deliverQueued(null, mail("always fails"))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
	}

	/** Without the broker the mail is still sent, at once, as before the queue. */
	@Test
	void whenTheQueueCannotBeReachedTheMailIsSentAtOnce() {
		mailer = withoutTheQueue(mail -> {
			if (mail.subject().equals("fails")) {
				throw new IllegalStateException("535 authentication failed for alice@example.com");
			}
			delivered.add(mail.subject());
		});

		assertThatCode(() -> mailer.send(mail("fails"))).doesNotThrowAnyException();
		mailer.send(mail("works"));

		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).containsExactly("works");
	}

	/** Nothing that could be logged shows the address, the text, or the password of the mail account. */
	@Test
	void theTextOfAMailAndTheMailPasswordAreNotPartOfWhatIsPrinted() {
		assertThat(mail("Your OpsFlow verification code").toString()).contains("Your OpsFlow verification code")
				.doesNotContain("123456").doesNotContain("alice@example.com");
		OpsflowProperties.Mail settings = new OpsflowProperties.Mail("smtp.example.com", 587, "sender@example.com",
				"the-mail-password", "", TTL);
		assertThat(settings.toString()).doesNotContain("the-mail-password");
		assertThat(settings.sender()).isEqualTo("sender@example.com");
		assertThat(settings.configured()).isTrue();
		assertThat(new OpsflowProperties.Mail("", 587, "", "", "", TTL).configured()).isFalse();
		// A host with no password yet is not a mail server that can be used.
		assertThat(new OpsflowProperties.Mail("smtp.example.com", 587, "sender@example.com", "", "", TTL).configured()).isFalse();
	}

}
