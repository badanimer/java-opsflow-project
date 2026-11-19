package com.opsflow.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.opsflow.common.error.ApiException;

/**
 * The five accounts of the demo data (see Demo data in docs/backend/backend-design.md), one for each fixed
 * role, made at the console as the first administrator is: an account cannot be made through the API without
 * an email code, and only an administrator can give a role.
 *
 * They share one password, typed by the person who runs the command and checked with the rule of sign-up.
 * It is hashed once, before any transaction, and each account is made in a transaction of its own by
 * {@link AdminCreator#createWithRole}. Nothing is made when one of the usernames is taken, because then the
 * demo data is there already.
 */
@Component
public class DemoAccounts {

	/** A demo account: its username and the one role it holds. */
	public record Account(String username, String role) {
	}

	/** The accounts, in the order they are made: an administrator, a manager, two operators, and a viewer. */
	public static final List<Account> ACCOUNTS = List.of(new Account("adam", "ADMIN"), new Account("mary", "MANAGER"),
			new Account("olive", "OPERATOR"), new Account("otto", "OPERATOR"), new Account("vera", "VIEWER"));

	private final AuthRepository repository;
	private final AdminCreator creator;
	private final PasswordHasher hasher;

	DemoAccounts(AuthRepository repository, AdminCreator creator, PasswordHasher hasher) {
		this.repository = repository;
		this.creator = creator;
		this.hasher = hasher;
	}

	/** The username of a demo account that exists already, or null when none does. */
	public String takenUsername() {
		return ACCOUNTS.stream().map(Account::username).filter(repository::usernameExists).findFirst().orElse(null);
	}

	/**
	 * Makes the five accounts with the password.
	 *
	 * @return the id of each account by its username
	 * @throws AdminCreator.Refused when an account exists already or the password breaks the rule of sign-up
	 */
	public Map<String, Long> create(String password) {
		try {
			AccountRules.checkPassword("Password", password);
		}
		catch (ApiException weak) {
			throw new AdminCreator.Refused("The password must have 8 to 24 characters, with at least one letter and one digit.");
		}
		String taken = takenUsername();
		if (taken != null) {
			throw new AdminCreator.Refused("The account " + taken + " exists already, so the demo data is there.");
		}
		String hash = hasher.hash(password);
		Map<String, Long> ids = new LinkedHashMap<>();
		for (Account account : ACCOUNTS) {
			ids.put(account.username(), creator.createWithRole(account.username(), account.username() + "@example.com", hash, account.role()));
		}
		return ids;
	}

}
