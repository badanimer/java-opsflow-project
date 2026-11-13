package com.opsflow.common.mail;

/**
 * A plain-text mail to one address. The text may hold a code, so a mail is never logged: the text of this
 * record names the subject only.
 */
public record OutgoingMail(String to, String subject, String text) {

	@Override
	public String toString() {
		return "OutgoingMail[subject=" + subject + "]";
	}

}
