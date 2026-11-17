package com.opsflow.common.mail;

/**
 * Where {@link Mailer} puts a mail so that it outlives a restart: the queue of RabbitMQ in the application
 * ({@link RabbitMailQueue}). A listener takes the mail from there and gives it to {@link Mailer#deliverQueued}.
 */
public interface MailQueue {

	/**
	 * Puts the mail in the queue and returns once the broker has confirmed that it holds it.
	 *
	 * @param id the id of the mail, which comes back with it
	 * @throws RuntimeException when the broker cannot be reached or does not confirm; the caller then sends
	 *     the mail itself
	 */
	void publish(String id, OutgoingMail mail);

}
