package com.opsflow.ticket;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Makes the number of a new ticket: 8 characters from {@code A-Z} and {@code 0-9}, from a secure random
 * source (T-12; see Order and ticket numbers in docs/database/database-design.md). The unique key
 * {@code uk_tickets_ticket_no} decides whether a number is free: on a clash the caller asks for another.
 */
@Component
public class TicketNumbers {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int LENGTH = 8;
	private static final Pattern FORM = Pattern.compile("[A-Z0-9]{8}");

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder number = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return number.toString();
	}

	/**
	 * A number a person typed, in upper case and without the spaces around it, or null when it cannot be a
	 * ticket number. The column compares exactly, and the numbers are made in upper case.
	 */
	public static String typed(String text) {
		if (text == null) {
			return null;
		}
		String number = text.strip().toUpperCase(Locale.ROOT);
		return FORM.matcher(number).matches() ? number : null;
	}

}
