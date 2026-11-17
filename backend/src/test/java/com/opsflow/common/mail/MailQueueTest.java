package com.opsflow.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;

import com.opsflow.support.MySqlIntegrationTest;

/** The mail queue against a real RabbitMQ in a container (see Mail in docs/backend/backend-design.md). */
class MailQueueTest extends MySqlIntegrationTest {

	@Autowired
	Mailer mailer;

	@Autowired
	AmqpAdmin admin;

	@Test
	void aMailGoesThroughTheQueueToTheMailServerAndLeavesNothingBehind() {
		mailer.send(new OutgoingMail("alice@example.com", "Your OpsFlow verification code", "The code is 123456."));

		assertThat(sentMail()).extracting(OutgoingMail::to).containsExactly("alice@example.com");
		assertThat(messages(RabbitMailQueue.QUEUE)).isZero();
	}

	/** A mail the server refuses three times is kept as a dead letter for a day, and the next mail goes on. */
	@Test
	void aMailTheServerRefusesEndsAsADeadLetter() {
		admin.purgeQueue(RabbitMailQueue.DEAD, false);
		mailbox.refuseEveryMail();

		mailer.send(new OutgoingMail("alice@example.com", "Your OpsFlow verification code", "The code is 123456."));

		assertThat(mailer.flush(Duration.ofSeconds(20))).isTrue();
		// The broker moves the rejected mail a moment after the listener gave it up.
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (messages(RabbitMailQueue.DEAD) == 0 && System.nanoTime() < deadline) {
			Thread.onSpinWait();
		}
		assertThat(messages(RabbitMailQueue.DEAD)).isEqualTo(1);
		assertThat(messages(RabbitMailQueue.QUEUE)).isZero();
		assertThat(sentMail()).isEmpty();
		admin.purgeQueue(RabbitMailQueue.DEAD, false);
	}

	private int messages(String queue) {
		Properties properties = admin.getQueueProperties(queue);
		return ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
	}

}
