package com.opsflow.auth;

import java.util.regex.Pattern;

import com.opsflow.common.error.ApiException;

/**
 * The rules for a new username and a new password (see Password rule in
 * docs/database/database-design.md). Sign-up, the password reset, and the console command
 * that creates an administrator use them.
 */
final class AccountRules {

	static final int PASSWORD_MIN = 8;
	static final int PASSWORD_MAX = 24;

	/** 3 to 64 letters and digits. No space and no other character. */
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9]{3,64}");

	private AccountRules() {
	}

	static void checkUsername(String field, String username) {
		if (!USERNAME.matcher(username).matches()) {
			throw ApiException.invalid(field, "must be 3 to 64 letters and digits, with no spaces or other characters");
		}
	}

	/** 8 to 24 characters with at least one letter and at least one digit. */
	static void checkPassword(String field, String password) {
		boolean length = password.length() >= PASSWORD_MIN && password.length() <= PASSWORD_MAX;
		boolean letter = password.chars().anyMatch(Character::isLetter);
		boolean digit = password.chars().anyMatch(Character::isDigit);
		if (!length || !letter || !digit) {
			throw ApiException.invalid(field, "must be 8 to 24 characters with at least one letter and one digit");
		}
	}

}
