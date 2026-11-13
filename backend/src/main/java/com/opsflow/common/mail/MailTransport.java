package com.opsflow.common.mail;

/**
 * Hands one mail to a mail server and waits for the answer. It is slow and may fail, so nothing calls it
 * directly: a mail is sent through {@link Mailer}, which calls this on its own thread.
 */
public interface MailTransport {

	void send(OutgoingMail mail);

}
