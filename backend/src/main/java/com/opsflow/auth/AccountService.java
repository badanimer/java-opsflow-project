package com.opsflow.auth;

import org.springframework.stereotype.Service;

import com.opsflow.auth.AccountWriter.Created;
import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.auth.TokenService.IssuedToken;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;

/**
 * Sign-up and the password reset (T-29). This class has no transaction: like the login, it does the slow
 * work first and then calls a short transaction of {@link AccountWriter} through {@link TransactionRetry}.
 *
 * The order is the same for both: the cheap checks of the values, then the code, and only with a right
 * code the BCrypt work. A request with a wrong code therefore costs no hashing, and its wrong entry is
 * counted.
 */
@Service
public class AccountService {

	/**
	 * @param id the id of the new user
	 * @param token the login token of the new user, who goes straight to the pending page
	 */
	public record NewAccount(long id, String username, IssuedToken token) {
	}

	private final EmailCodeService codes;
	private final PasswordHasher hasher;
	private final AccountWriter writer;
	private final AuthRepository repository;
	private final TransactionRetry retry;
	private final TokenService tokens;
	private final LoginLimits limits;

	AccountService(EmailCodeService codes, PasswordHasher hasher, AccountWriter writer, AuthRepository repository,
			TransactionRetry retry, TokenService tokens, LoginLimits limits) {
		this.codes = codes;
		this.limits = limits;
		this.hasher = hasher;
		this.writer = writer;
		this.repository = repository;
		this.retry = retry;
		this.tokens = tokens;
	}

	public NewAccount signUp(String username, String email, String password, String code, String ipAddress) {
		AccountRules.checkUsername("username", username);
		AccountRules.checkPassword("password", password);
		long codeId = codes.requireMatch(email, EmailCodePurpose.REGISTER, code);

		String passwordHash = hasher.hash(password);

		Created created = retry.run(() -> writer.create(codeId, username, email, passwordHash, ipAddress));
		return new NewAccount(created.id(), username, tokens.issue(created.id(), created.issuedAt(), created.sessionId()));
	}

	public void resetPassword(String email, String code, String newPassword, String ipAddress) {
		AccountRules.checkPassword("newPassword", newPassword);
		long codeId = codes.requireMatch(email, EmailCodePurpose.RESET_PASSWORD, code);
		// A code is mailed only for an account that can be recovered, so this holds unless it changed since.
		Account account = repository.findByEmail(email).filter(found -> !found.deleted() && !found.system())
				.orElseThrow(EmailCodeService::invalidCode);

		// The code is right, so the person may learn this. The code is not used up and works for another password.
		if (hasher.matches(newPassword, account.passwordHash())) {
			throw ApiException.invalid("newPassword", "must differ from the current password");
		}
		String newHash = hasher.hash(newPassword);

		boolean done = retry.run(() -> writer.resetPassword(codeId, account.id(), account.passwordHash(), newHash, ipAddress));
		if (!done) {
			throw EmailCodeService.invalidCode();
		}
		// The owner proved the address, so every address locked out of the account may try again.
		limits.clearAccount(account.id());
	}

}
