package com.opsflow.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.opsflow.common.mail.MailTransport;
import com.opsflow.common.mail.OutgoingMail;

/**
 * The mail server of the integration tests: it keeps every mail and sends none, so the tests need no
 * mail account and no network. The application still sends through its real {@code Mailer}, on the
 * mailer thread, so a test reads the mails through {@code MySqlIntegrationTest.sentMail()}, which waits
 * for that thread first.
 */
public final class Mailbox implements MailTransport {

	/** Replaces the mail server of the application with this one. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		@Primary
		Mailbox testMailbox() {
			return new Mailbox();
		}
	}

	private final List<OutgoingMail> mails = new CopyOnWriteArrayList<>();
	private volatile boolean refusing;

	@Override
	public void send(OutgoingMail mail) {
		if (refusing) {
			throw new IllegalStateException("550 the test mail server refuses every mail");
		}
		mails.add(mail);
	}

	/** From now on the mail server refuses every mail, as a server that is down would, until the next test. */
	public void refuseEveryMail() {
		refusing = true;
	}

	List<OutgoingMail> mails() {
		return List.copyOf(mails);
	}

	void clear() {
		mails.clear();
		refusing = false;
	}

}
