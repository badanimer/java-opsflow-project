package com.opsflow.auth;

/** What an email code is for. The names are the values of {@code email_verification_codes.purpose}. */
public enum EmailCodePurpose {

	REGISTER("signing up"),
	RESET_PASSWORD("setting a new password");

	private final String forWhat;

	EmailCodePurpose(String forWhat) {
		this.forWhat = forWhat;
	}

	/** The words that finish "Your OpsFlow code for ..." in the mail. */
	String forWhat() {
		return forWhat;
	}

}
