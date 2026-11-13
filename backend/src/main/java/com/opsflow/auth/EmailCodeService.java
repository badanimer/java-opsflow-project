package com.opsflow.auth;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.auth.EmailCodeRepository.Code;
import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.mail.Mailer;
import com.opsflow.common.mail.OutgoingMail;
import com.opsflow.common.retry.TransactionRetry;

/**
 * Email codes (T-29): requesting one, and checking the one a person typed. This class has no transaction
 * of its own.
 *
 * <b>Sign-up</b> says so when the address already has an account ({@code EMAIL_TAKEN}): the owner chose
 * the clear message over hiding which addresses are registered.
 *
 * <b>A request for a new password is answered the same way whether or not a mail is sent.</b> The code is
 * mailed only when the address belongs to an account that is not soft-deleted and is not the SYSTEM
 * identity. Otherwise a row is stored all the same, with a value that no code matches, so the limits, the
 * 60-second rule, and a later wrong entry behave exactly as for a mailed code, and Forgot password does
 * not show which addresses are registered. The mail is sent after the transaction has committed, in the
 * background (see {@link Mailer}).
 */
@Service
public class EmailCodeService {

	private final EmailCodeRepository repository;
	private final EmailCodeStore store;
	private final EmailCodeHasher hasher;
	private final AuthRepository accounts;
	private final TransactionRetry retry;
	private final Mailer mailer;
	private final OpsflowProperties.EmailCode settings;
	private final Clock clock;

	EmailCodeService(EmailCodeRepository repository, EmailCodeStore store, EmailCodeHasher hasher, AuthRepository accounts,
			TransactionRetry retry, Mailer mailer, OpsflowProperties properties, Clock clock) {
		this.repository = repository;
		this.store = store;
		this.hasher = hasher;
		this.accounts = accounts;
		this.retry = retry;
		this.mailer = mailer;
		this.settings = properties.emailCode();
		this.clock = clock;
	}

	public void request(String email, EmailCodePurpose purpose, String ipAddress) {
		Optional<Account> account = accounts.findByEmail(email);
		if (purpose == EmailCodePurpose.REGISTER && account.isPresent()) {
			// The owner's choice: a person who signs up is told that the address has an account. A soft-deleted
			// account and the SYSTEM identity keep their addresses, so they count. Nothing is stored or counted.
			throw AccountWriter.emailTaken();
		}
		boolean send = purpose == EmailCodePurpose.REGISTER
				|| account.filter(found -> !found.deleted() && !found.system()).isPresent();
		String code = hasher.newCode();
		String stored = send ? hasher.hash(purpose, email, code) : EmailCodeHasher.NOT_SENT;

		retry.run(() -> store.store(email, purpose, stored, ipAddress));

		if (send) {
			mailer.send(new OutgoingMail(email, "Your OpsFlow verification code",
					"Your OpsFlow code for " + purpose.forWhat() + " is " + code + ".\n\n" + "It is valid for "
							+ settings.ttl().toMinutes() + " minutes. If you did not ask for it, you can ignore this mail.\n"));
		}
	}

	/**
	 * Checks the code a person typed against the latest unused code of the address.
	 *
	 * @return the id of the code, which the caller uses up in the transaction of its change
	 * @throws ApiException {@code CODE_INVALID} when there is no such code, or it has expired, or it has had
	 *     its wrong entries, or the entry is wrong. A wrong entry on a valid code is counted first
	 */
	long requireMatch(String email, EmailCodePurpose purpose, String entry) {
		Code code = repository.findUnused(email, purpose).orElse(null);
		boolean valid = code != null && code.expiresAt().isAfter(clock.instant()) && code.attempts() < settings.maxWrongEntries();
		// Worked out in every case, with the address as it was stored, so the answer takes the same time.
		boolean matched = hasher.matches(purpose, code != null ? code.email() : email, entry,
				code != null ? code.codeHash() : EmailCodeHasher.NOT_SENT);
		if (valid && matched) {
			return code.id();
		}
		if (valid) {
			retry.run(() -> store.recordWrongEntry(code.id()));
		}
		throw invalidCode();
	}

	/** One answer for every reason, so it does not show whether a code exists for the address. */
	static ApiException invalidCode() {
		return new ApiException(ErrorCode.CODE_INVALID, "The code is wrong or no longer valid. Request a new one.");
	}

}
