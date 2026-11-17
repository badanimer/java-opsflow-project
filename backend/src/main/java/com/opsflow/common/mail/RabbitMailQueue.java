package com.opsflow.common.mail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * The mail queue in RabbitMQ (Q-07; see Mail in docs/backend/backend-design.md).
 *
 * <ul>
 * <li><b>Durable.</b> The queue survives a restart of the broker, and each mail is persistent, so a mail
 * that was handed over is sent even when the application stops before sending it.</li>
 * <li><b>Short-lived.</b> A mail holds a code that is valid for ten minutes, so it expires in the queue
 * after {@code opsflow.mail.queue-ttl}. A code that arrives later is of no use.</li>
 * <li><b>Dead letters.</b> A mail that the mail server refused after its tries, or that expired, goes to
 * {@value #DEAD}, where it is kept for a day so that a person can see that mail failed. The text of a mail is
 * never logged.</li>
 * <li><b>In order.</b> One consumer takes one mail at a time.</li>
 * </ul>
 */
@Component
public class RabbitMailQueue implements MailQueue {

	static final String QUEUE = "opsflow.mail";
	static final String DEAD = "opsflow.mail.dead";
	private static final long CONFIRM_MILLIS = 5_000;
	private static final Duration DEAD_KEPT = Duration.ofDays(1);

	/** The body of a message: the mail, as JSON. The id travels as the message id. */
	record Body(String to, String subject, String text) {
	}

	@Configuration
	static class Queues {

		@Bean
		Queue mailQueue() {
			return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DEAD).build();
		}

		@Bean
		Queue deadMailQueue() {
			return QueueBuilder.durable(DEAD).ttl((int) DEAD_KEPT.toMillis()).build();
		}
	}

	private final RabbitTemplate rabbit;
	private final JsonMapper json;
	private final Duration ttl;

	RabbitMailQueue(RabbitTemplate rabbit, JsonMapper json, OpsflowProperties properties) {
		this.rabbit = rabbit;
		this.json = json;
		this.ttl = properties.mail().queueTtl();
	}

	@Override
	public void publish(String id, OutgoingMail mail) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId(id);
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		properties.setExpiration(String.valueOf(ttl.toMillis()));
		byte[] body = json.writeValueAsBytes(new Body(mail.to(), mail.subject(), mail.text()));
		Message message = MessageBuilder.withBody(body).andProperties(properties).build();
		rabbit.invoke(operations -> {
			operations.send("", QUEUE, message);
			operations.waitForConfirmsOrDie(CONFIRM_MILLIS);
			return null;
		});
	}

	/** Takes the mails from the queue, one at a time, and gives each to the mailer. */
	@Component
	static class Listener {

		private final Mailer mailer;
		private final JsonMapper json;

		Listener(Mailer mailer, JsonMapper json) {
			this.mailer = mailer;
			this.json = json;
		}

		@RabbitListener(queues = QUEUE, concurrency = "1")
		void receive(Message message) {
			Body body = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Body.class);
			mailer.deliverQueued(message.getMessageProperties().getMessageId(), new OutgoingMail(body.to(), body.subject(), body.text()));
		}
	}

}
