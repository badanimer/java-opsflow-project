package com.opsflow.auth;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.opsflow.common.time.UtcTime;

/**
 * The SQL of login, of the token check, of sign-up, of the password reset, and of the console command that
 * creates an administrator. Every statement here touches {@code users}, {@code user_roles}, or {@code roles}.
 */
@Repository
class AuthRepository {

	/** What login and the token check need to know about an account. */
	record Account(long id, String username, String passwordHash, Instant passwordChangedAt, boolean disabled,
			boolean deleted, boolean system, int failedLoginCount, Instant lastFailedLoginAt, Instant lockedUntil,
			Instant lastLoginAt) {
	}

	private static final String SELECT = """
			SELECT id, username, password_hash, password_changed_at, status, deleted, is_system,
			       failed_login_count, last_failed_login_at, locked_until, last_login_at
			FROM users
			""";

	private final JdbcTemplate jdbc;

	AuthRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Account> findByUsername(String username) {
		return jdbc.query(SELECT + "WHERE username = ?", AuthRepository::account, username).stream().findFirst();
	}

	Optional<Account> findById(long id) {
		return jdbc.query(SELECT + "WHERE id = ?", AuthRepository::account, id).stream().findFirst();
	}

	/** Reads the account and holds its row until the transaction ends. */
	Optional<Account> lockById(long id) {
		return jdbc.query(SELECT + "WHERE id = ? FOR UPDATE", AuthRepository::account, id).stream().findFirst();
	}

	List<String> roleCodes(long userId) {
		return jdbc.queryForList(
				"SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.code",
				String.class, userId);
	}

	/** Called with the row locked, so the count that was read is still the count. */
	void recordFailure(long id, int failedLoginCount, Instant at, Instant lockedUntil) {
		jdbc.update("UPDATE users SET failed_login_count = ?, last_failed_login_at = ?, locked_until = ? WHERE id = ?",
				failedLoginCount, UtcTime.toLocalDateTime(at), UtcTime.toLocalDateTime(lockedUntil), id);
	}

	void recordSuccess(long id, Instant at) {
		jdbc.update("UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = ? WHERE id = ?",
				UtcTime.toLocalDateTime(at), id);
	}

	/** The account of an email address, soft-deleted accounts and the SYSTEM identity included: their addresses stay reserved. */
	Optional<Account> findByEmail(String email) {
		return jdbc.query(SELECT + "WHERE email = ?", AuthRepository::account, email).stream().findFirst();
	}

	boolean usernameExists(String username) {
		return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE username = ?)", Boolean.class, username);
	}

	/**
	 * A new account: active, with no role. The unique keys on the username and the email refuse a second one.
	 *
	 * @param loggedInAt the moment of the first login when the account is logged in at once, or null
	 */
	long insertUser(String username, String email, String passwordHash, Instant loggedInAt) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO users (username, email, password_hash, last_login_at) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, username);
			statement.setString(2, email);
			statement.setString(3, passwordHash);
			statement.setObject(4, UtcTime.toLocalDateTime(loggedInAt));
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	/** Reads the id of a role and holds its row until the transaction ends, or returns empty when there is no such role. */
	Optional<Long> lockRoleId(String code) {
		return jdbc.queryForList("SELECT id FROM roles WHERE code = ? FOR UPDATE", Long.class, code).stream().findFirst();
	}

	/** The unique key on the user and the role refuses a second grant of the same role. */
	void grantRole(long userId, long roleId) {
		jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
	}

	/** Called with the row locked. A reset also clears the lock and the count of failed logins. */
	void resetPassword(long id, String passwordHash, Instant changedAt) {
		jdbc.update("""
				UPDATE users SET password_hash = ?, password_changed_at = ?, failed_login_count = 0,
				       last_failed_login_at = NULL, locked_until = NULL
				WHERE id = ?
				""", passwordHash, UtcTime.toLocalDateTime(changedAt), id);
	}

	private static Account account(ResultSet rs, int row) throws SQLException {
		return new Account(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
				instant(rs, "password_changed_at"), "DISABLED".equals(rs.getString("status")), rs.getBoolean("deleted"),
				rs.getBoolean("is_system"), rs.getInt("failed_login_count"), instant(rs, "last_failed_login_at"),
				instant(rs, "locked_until"), instant(rs, "last_login_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return UtcTime.toInstant(rs.getObject(column, LocalDateTime.class));
	}

}
