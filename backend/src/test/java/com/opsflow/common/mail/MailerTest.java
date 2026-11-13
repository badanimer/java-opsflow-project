package com.opsflow.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.config.OpsflowProperties;

/** The mailer, with a mail server made by the test and no database. */
class MailerTest {

	private static final Duration WAIT = Duration.ofSeconds(10);

	private final List<String> delivered = new CopyOnWriteArrayList<>();
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

	@Test
	void mailsLeaveInTheOrderTheyWereHandedOver() {
		mailer = new Mailer(mail -> delivered.add(mail.subject()));

		for (int i = 1; i <= 20; i++) {
			mailer.send(mail("mail " + i));
		}

		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).hasSize(20).startsWith("mail 1", "mail 2", "mail 3").endsWith("mail 20");
	}

	/** A mail server is slow and a mail cannot be rolled back, so a mail is sent after the commit. */
	@Test
	void sendingInsideATransactionIsRefused() {
		mailer = new Mailer(mail -> delivered.add(mail.subject()));
		TransactionSynchronizationManager.setActualTransactionActive(true);

		assertThatThrownBy(() -> mailer.send(mail("inside"))).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("after the transaction has committed");

		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertThat(mailer.flush(WAIT)).isTrue();
		assertThat(delivered).isEmpty();
	}

	/** The caller returns while the mail server is still busy, so the answer does not wait for it. */
	@Test
	void theCallerDoesNotWaitForTheMailServer() throws InterruptedException {
		CountDownLatch serverMayAnswer = new CountDownLatch(1);
		mailer = new Mailer(mail -> {
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

	@Test
	void aFailingMailServerDoesNotReachTheCallerOrStopTheNextMail() {
		mailer = new Mailer(mail -> {
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
				"the-mail-password", "");
		assertThat(settings.toString()).doesNotContain("the-mail-password");
		assertThat(settings.sender()).isEqualTo("sender@example.com");
		assertThat(settings.configured()).isTrue();
		assertThat(new OpsflowProperties.Mail("", 587, "", "", "").configured()).isFalse();
		// A host with no password yet is not a mail server that can be used.
		assertThat(new OpsflowProperties.Mail("smtp.example.com", 587, "sender@example.com", "", "").configured()).isFalse();
	}

}
