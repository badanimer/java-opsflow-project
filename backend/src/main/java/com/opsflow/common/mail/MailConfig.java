package com.opsflow.common.mail;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.opsflow.common.config.OpsflowProperties;

/**
 * Chooses how mail leaves the application, from {@code opsflow.mail.*}.
 *
 * With a host, mail goes to that server by SMTP with STARTTLS, which every provider offers, so changing
 * the provider changes the configuration only. Without a host or a password nothing is sent and a warning is logged,
 * so the application starts on a machine that has no mail account. The password comes from the
 * environment and is never logged.
 */
@Configuration
public class MailConfig {

	private static final Logger log = LoggerFactory.getLogger(MailConfig.class);
	private static final String TIMEOUT_MILLIS = "10000";

	@Bean
	MailTransport mailTransport(OpsflowProperties properties) {
		OpsflowProperties.Mail mail = properties.mail();
		if (!mail.configured()) {
			log.warn("No mail server is configured (OPSFLOW_MAIL_HOST or OPSFLOW_MAIL_PASSWORD is not set): no mail will be sent");
			return message -> log.warn("Mail not sent, no mail server is configured: {}", message.subject());
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(mail.host());
		sender.setPort(mail.port());
		sender.setUsername(mail.username());
		sender.setPassword(mail.password());
		sender.setDefaultEncoding("UTF-8");
		Properties smtp = sender.getJavaMailProperties();
		smtp.setProperty("mail.smtp.auth", "true");
		// The password and the mail never travel over a connection that is not encrypted.
		smtp.setProperty("mail.smtp.starttls.enable", "true");
		smtp.setProperty("mail.smtp.starttls.required", "true");
		smtp.setProperty("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
		smtp.setProperty("mail.smtp.timeout", TIMEOUT_MILLIS);
		smtp.setProperty("mail.smtp.writetimeout", TIMEOUT_MILLIS);
		String from = mail.sender();
		return message -> {
			SimpleMailMessage smtpMessage = new SimpleMailMessage();
			smtpMessage.setFrom(from);
			smtpMessage.setTo(message.to());
			smtpMessage.setSubject(message.subject());
			smtpMessage.setText(message.text());
			sender.send(smtpMessage);
		};
	}

}
