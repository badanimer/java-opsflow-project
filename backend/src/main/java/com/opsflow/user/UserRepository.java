package com.opsflow.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.opsflow.common.paging.PageQuery;

/**
 * The SQL of the user page and of the changes one user makes to another: the list, the locks, the roles
 * of a user, and the count of usable administrators. Every statement here touches {@code users}, {@code user_roles}, or {@code roles}.
 */
@Repository
class UserRepository {

	/**
	 * A user row as a change needs it.
	 *
	 * @param active whether {@code status} is {@code ACTIVE}
	 * @param deleted whether the account is soft-deleted
	 * @param system whether the row is the SYSTEM identity, which is not a person
	 */
	record UserRow(long id, String username, boolean active, boolean deleted, boolean system) {

		/** A usable account: active and not soft-deleted (see Role grants in docs/database/database-design.md). */
		boolean usable() {
			return active && !deleted;
		}
	}

	/**
	 * A row of the user page.
	 *
	 * @param roles the role codes, in the order of the codes; empty for a user who waits for a role
	 */
	record ListedUser(long id, String username, String status, List<String> roles) {
	}

	/**
	 * What the user page may show and what it is narrowed to. Every field narrows the list, and none
	 * widens it.
	 *
	 * @param hiddenRoles a user who holds any of these roles is not listed; empty to list every holder
	 * @param hiddenUserId a user who is not listed (the viewer), or null
	 * @param usernamePart a part of the username, matched without regard to case, or null for any
	 * @param roleFilter the role codes of the role buttons; a user who holds any of them is listed
	 * @param withoutRole whether the button No role is chosen: a user who holds no role is listed too
	 */
	record ListFilter(List<String> hiddenRoles, Long hiddenUserId, String usernamePart, List<String> roleFilter,
			boolean withoutRole) {

		/** Whether a role button is chosen. With none the list is not narrowed by role. */
		boolean byRole() {
			return !roleFilter.isEmpty() || withoutRole;
		}
	}

	/** The character that makes {@code %}, {@code _}, and itself plain characters in a {@code LIKE} pattern. */
	private static final char LIKE_ESCAPE = '!';

	private final JdbcTemplate jdbc;

	UserRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Reads the user and holds the row until the transaction ends. The status and the roles of a user
	 * change only in a transaction that holds this row, so what is read after it stays true until the commit.
	 */
	Optional<UserRow> lockById(long id) {
		return jdbc.query("SELECT id, username, status, deleted, is_system FROM users WHERE id = ? FOR UPDATE",
				(rs, row) -> new UserRow(rs.getLong("id"), rs.getString("username"), "ACTIVE".equals(rs.getString("status")),
						rs.getBoolean("deleted"), rs.getBoolean("is_system")),
				id).stream().findFirst();
	}

	/**
	 * A page of the user page, ordered by username and then {@code id}, from A to Z. The SYSTEM identity
	 * and the soft-deleted users are never listed. The query reads no lock: a list shows what was committed.
	 *
	 * @param page the paging; its order is ascending
	 */
	List<ListedUser> list(ListFilter filter, PageQuery page) {
		StringBuilder sql = new StringBuilder("""
				SELECT u.id, u.username, u.status,
				       (SELECT GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ',')
				        FROM user_roles ur JOIN roles r ON r.id = ur.role_id
				        WHERE ur.user_id = u.id) AS role_codes
				FROM users u
				WHERE u.deleted = FALSE AND u.is_system = FALSE
				""");
		List<Object> args = new ArrayList<>();
		if (!filter.hiddenRoles().isEmpty()) {
			sql.append(" AND NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.code IN (")
					.append(placeholders(filter.hiddenRoles().size())).append("))");
			args.addAll(filter.hiddenRoles());
		}
		if (filter.hiddenUserId() != null) {
			sql.append(" AND u.id <> ?");
			args.add(filter.hiddenUserId());
		}
		if (filter.usernamePart() != null) {
			// The column compares without regard to case, so this finds 'Natalie' for 'ali'.
			sql.append(" AND u.username LIKE ? ESCAPE '").append(LIKE_ESCAPE).append("'");
			args.add("%" + likeLiteral(filter.usernamePart()) + "%");
		}
		if (filter.byRole()) {
			List<String> either = new ArrayList<>();
			if (!filter.roleFilter().isEmpty()) {
				either.add("EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.code IN ("
						+ placeholders(filter.roleFilter().size()) + "))");
				args.addAll(filter.roleFilter());
			}
			if (filter.withoutRole()) {
				either.add("NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id)");
			}
			sql.append(" AND (").append(String.join(" OR ", either)).append(")");
		}
		boolean upward = page.ascendingRead();
		if (page.cursor() != null) {
			String beyond = upward ? ">" : "<";
			sql.append(" AND (u.username ").append(beyond).append(" ? OR (u.username = ? AND u.id ").append(beyond).append(" ?))");
			args.add(page.cursor().key());
			args.add(page.cursor().key());
			args.add(page.cursor().id());
		}
		String way = upward ? "ASC" : "DESC";
		sql.append(" ORDER BY u.username ").append(way).append(", u.id ").append(way).append(" LIMIT ?");
		args.add(page.fetchSize());

		return jdbc.query(sql.toString(), (rs, row) -> {
			String codes = rs.getString("role_codes");
			return new ListedUser(rs.getLong("id"), rs.getString("username"), rs.getString("status"),
					codes == null ? List.of() : List.of(codes.split(",")));
		}, args.toArray());
	}

	private static String placeholders(int count) {
		return String.join(", ", Collections.nCopies(count, "?"));
	}

	/** The text as a {@code LIKE} pattern that matches only itself. */
	private static String likeLiteral(String text) {
		StringBuilder pattern = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
				pattern.append(LIKE_ESCAPE);
			}
			pattern.append(c);
		}
		return pattern.toString();
	}

	/** The codes of the roles the user holds, in the order of the codes. Call it with the user row locked. */
	List<String> roleCodes(long userId) {
		return jdbc.queryForList(
				"SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.code",
				String.class, userId);
	}

	/** The id of every role, by its code. The roles are fixed, so this needs no lock. */
	Map<String, Long> roleIds() {
		Map<String, Long> ids = new LinkedHashMap<>();
		jdbc.query("SELECT code, id FROM roles ORDER BY code", rs -> {
			ids.put(rs.getString("code"), rs.getLong("id"));
		});
		return ids;
	}

	/**
	 * Holds the row of a role until the transaction ends. Every change that could take the last usable
	 * holder of the role away takes this lock first, so two of them run one after the other.
	 */
	void lockRole(String code) {
		jdbc.queryForList("SELECT id FROM roles WHERE code = ? FOR UPDATE", Long.class, code);
	}

	/** The unique key on the user and the role refuses a second grant of the same role. */
	void grant(long userId, long roleId) {
		jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
	}

	void revoke(long userId, long roleId) {
		jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_id = ?", userId, roleId);
	}

	/** Called with the row locked, after the status was read under that lock. */
	void setStatus(long userId, String status) {
		jdbc.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
	}

	/**
	 * Marks the account as removed, and disables it (see Identity and Access). The roles, the username,
	 * and the email stay. Called with the row locked.
	 */
	void softDelete(long userId) {
		jdbc.update("UPDATE users SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", userId);
	}

	/** How many usable accounts hold the role now, the changes of this transaction included. */
	int countUsableHolders(String roleCode) {
		return jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM user_roles ur
				JOIN roles r ON r.id = ur.role_id
				JOIN users u ON u.id = ur.user_id
				WHERE r.code = ? AND u.status = 'ACTIVE' AND u.deleted = FALSE AND u.is_system = FALSE
				""", Integer.class, roleCode);
	}

}
