package com.opsflow.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;

/**
 * The transaction of the console command that creates an administrator (T-24; see First administrator
 * in docs/database/database-design.md).
 *
 * It locks the {@code ADMIN} row in {@code roles}, creates the user, grants {@code ADMIN}, and writes
 * the two log rows, all in one transaction. The role row is locked for the same reason as in
 * Administrator protection: a change to who holds {@code ADMIN} waits for any other such change. The
 * new row of {@code users} is new, so nobody can be waiting for it. The password was hashed before
 * this transaction started.
 *
 * Nobody is logged in at the console, so the log rows have no operator.
 *
 * {@link #createWithRole} is the same transaction with another of the fixed roles, for the accounts of the
 * demo data ({@link DemoAccounts}).
 */
@Component
public class AdminCreator {

	static final String ADMIN = "ADMIN";
	static final String CONSOLE = "console";

	/** Why an administrator could not be created, in words for the person at the console. */
	public static class Refused extends RuntimeException {

		Refused(String message) {
			super(message);
		}
	}

	private final AuthRepository repository;
	private final AuditLogWriter audit;

	AdminCreator(AuthRepository repository, AuditLogWriter audit) {
		this.repository = repository;
		this.audit = audit;
	}

	/** @return the id of the new administrator */
	@Transactional
	public long create(String username, String email, String passwordHash) {
		return createWithRole(username, email, passwordHash, ADMIN);
	}

	/** @return the id of the new user, who holds the one role given */
	@Transactional
	public long createWithRole(String username, String email, String passwordHash, String roleCode) {
		long roleId = repository.lockRoleId(roleCode).orElseThrow(() -> new Refused(
				"The " + roleCode + " role does not exist. Start the application once, so that the migrations create the roles."));
		if (repository.usernameExists(username)) {
			throw new Refused("This username is already used.");
		}
		if (repository.findByEmail(email).isPresent()) {
			throw new Refused("This email address already has an account.");
		}
		long id;
		try {
			id = repository.insertUser(username, email, passwordHash, null, null);
		}
		catch (DuplicateKeyException sameMoment) {
			throw new Refused("This username or email address was taken a moment ago.");
		}
		repository.grantRole(id, roleId);

		Map<String, Object> account = new LinkedHashMap<>();
		account.put("username", username);
		account.put("email", email);
		audit.write(AuditAction.USER_REGISTERED, null, AuditLogWriter.TARGET_USER, id, null, account, null, CONSOLE);

		Map<String, Object> grant = new LinkedHashMap<>();
		grant.put("roleId", roleId);
		grant.put("roleCode", roleCode);
		grant.put("username", username);
		audit.write(AuditAction.ROLE_GRANTED, null, AuditLogWriter.TARGET_USER, id, null, grant, null, CONSOLE);
		return id;
	}

}
