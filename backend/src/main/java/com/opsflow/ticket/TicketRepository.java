package com.opsflow.ticket;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.paging.Direction;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.time.DayRanges.Range;
import com.opsflow.common.time.UtcTime;

/**
 * The SQL of the ticket module: {@code tickets}, {@code ticket_assignment_history}, and
 * {@code ticket_inventory}, and the reads of {@code users}, {@code user_roles}, {@code inventory},
 * {@code products}, and {@code order_attempts} that the ticket rules need. A method that locks says so.
 * The lock order of the module is {@code users} (ascending id), then {@code tickets}, then
 * {@code inventory} for the items of a new ticket (see Concurrency by Module).
 */
@Repository
class TicketRepository {

	/** A person as an answer shows them. {@code system} is true only for the SYSTEM identity. */
	record Person(Long id, String username, boolean system) {
	}

	/** A user row read under a lock. */
	record UserLock(long id, String username, boolean active, boolean deleted, boolean system) {

		/** A usable account of a person: active, not deleted, and not the SYSTEM identity. */
		boolean usable() {
			return active && !deleted && !system;
		}
	}

	/** A ticket row read under a lock, with what the rules of a change need. */
	record Locked(long id, String ticketNo, TicketStatus status, long createdBy, Long assigneeId, Long resolvedBy) {
	}

	/** A row of a ticket list. */
	record Listed(long id, String ticketNo, String type, String source, String title, Person createdBy, Instant createdAt,
			TicketStatus status, Instant closedAt, Instant cancelledAt) {
	}

	/** The whole of one ticket row, with the people it names. */
	record Detail(long id, String ticketNo, String type, String source, TicketStatus status, String title, String description,
			String orderNo, Long followsTicketId, Person createdBy, Instant createdAt, Person assignee, String resolution,
			Person resolvedBy, Instant resolvedAt, Person closedBy, Instant closedAt, String cancelReason, Person cancelledBy,
			Instant cancelledAt, Long resolvedById) {
	}

	/** Another ticket that this one follows or that follows it, with what decides whether the caller may see it. */
	record Related(long id, String ticketNo, TicketStatus status, long createdBy, Long assigneeId, Long resolvedBy) {
	}

	/** An item a ticket is linked to. */
	record LinkedItem(long inventoryId, String sku, String name, boolean deleted) {
	}

	/** A row of the assignment history. */
	record HistoryRow(Person from, Person to, Person by, String reason, Instant at) {
	}

	/** An item read under a shared lock for a new link. */
	record SharedItem(long id, String name, boolean deleted) {
	}

	/** The filters of a ticket list, already checked. */
	record Filter(TicketStatus status, Long ownerId, String source, Long createdBy, Long assigneeId, Range range, String ticketNo,
			String orderNo) {
	}

	private static final String PEOPLE = """
			LEFT JOIN users cu ON cu.id = t.created_by
			LEFT JOIN users au ON au.id = t.assignee_id
			LEFT JOIN users ru ON ru.id = t.resolved_by
			LEFT JOIN users xu ON xu.id = t.closed_by
			LEFT JOIN users nu ON nu.id = t.cancelled_by
			""";

	private final JdbcTemplate jdbc;

	TicketRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Users

	/** Locks a user row with {@code FOR UPDATE}: the lock that the eligibility check and both limits stand on. */
	Optional<UserLock> lockUser(long id) {
		return jdbc.query("SELECT id, username, status, deleted, is_system FROM users WHERE id = ? FOR UPDATE",
				(rs, row) -> new UserLock(rs.getLong("id"), rs.getString("username"), "ACTIVE".equals(rs.getString("status")),
						rs.getBoolean("deleted"), rs.getBoolean("is_system")),
				id).stream().findFirst();
	}

	/** The codes of the roles a user holds. Called with the user row locked, so they cannot change meanwhile. */
	List<String> roleCodes(long userId) {
		return jdbc.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.code",
				String.class, userId);
	}

	/** The id of the SYSTEM identity, which the seed migration inserts once. */
	long systemUserId() {
		return jdbc.queryForObject("SELECT id FROM users WHERE is_system = TRUE", Long.class);
	}

	/** Tickets the user created that are {@code OPEN} now. Called with the user row locked. */
	int countOpenCreatedBy(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE created_by = ? AND status = 'OPEN'", Integer.class, userId);
	}

	/** Tickets assigned to the user that are {@code IN_PROGRESS} now. Called with the user row locked. */
	int countInProgress(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE assignee_id = ? AND status = 'IN_PROGRESS'", Integer.class, userId);
	}

	// Opening a ticket

	/** The id and status of the ticket with this number, with a plain read: a followed ticket never goes back. */
	Optional<Related> findByNo(String ticketNo) {
		return jdbc.query("SELECT id, ticket_no, status, created_by, assignee_id, resolved_by FROM tickets WHERE ticket_no = ?",
				(rs, row) -> related(rs), ticketNo).stream().findFirst();
	}

	/** Whether any order attempt has this number. An attempt is never changed or deleted, so no lock is needed. */
	boolean orderAttemptExists(String orderNo) {
		return !jdbc.queryForList("SELECT 1 FROM order_attempts WHERE order_no = ? LIMIT 1", Integer.class, orderNo).isEmpty();
	}

	/** Inserts a ticket. {@code uk_tickets_ticket_no} refuses a number issued before. */
	long insertTicket(String ticketNo, String type, String source, String title, String description, String orderNo, Long followsTicketId,
			long createdBy, Instant createdAt) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO tickets (ticket_no, order_no, follows_ticket_id, created_by, type, source, status, title, description,
					                     created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, ticketNo);
			statement.setString(2, orderNo);
			statement.setObject(3, followsTicketId);
			statement.setLong(4, createdBy);
			statement.setString(5, type);
			statement.setString(6, source);
			statement.setString(7, title);
			statement.setString(8, description);
			statement.setObject(9, UtcTime.toLocalDateTime(createdAt));
			statement.setObject(10, UtcTime.toLocalDateTime(createdAt));
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	/**
	 * Reads an item under a shared lock, for a new link: a deletion needs the row exclusively, so it waits for
	 * this ticket, or this ticket waits for it and sees the item deleted.
	 */
	Optional<SharedItem> shareItem(long inventoryId) {
		return jdbc.query("SELECT id, name, deleted_at FROM inventory WHERE id = ? FOR SHARE",
				(rs, row) -> new SharedItem(rs.getLong("id"), rs.getString("name"), rs.getObject("deleted_at") != null), inventoryId).stream()
				.findFirst();
	}

	/** The name of an item, with a plain read, for the answer that names a chosen item that does not exist. */
	Optional<String> itemName(long inventoryId) {
		return jdbc.queryForList("SELECT name FROM inventory WHERE id = ?", String.class, inventoryId).stream().findFirst();
	}

	void insertLink(long ticketId, long inventoryId, Instant at) {
		jdbc.update("INSERT INTO ticket_inventory (ticket_id, inventory_id, created_at) VALUES (?, ?, ?)", ticketId, inventoryId,
				UtcTime.toLocalDateTime(at));
	}

	// Changing a ticket

	/** Locks a ticket row with {@code FOR UPDATE}. Every change takes it after the user rows it needs. */
	Optional<Locked> lockTicket(long id) {
		return jdbc.query("SELECT id, ticket_no, status, created_by, assignee_id, resolved_by FROM tickets WHERE id = ? FOR UPDATE",
				(rs, row) -> new Locked(rs.getLong("id"), rs.getString("ticket_no"), TicketStatus.of(rs.getString("status")),
						rs.getLong("created_by"), nullableLong(rs, "assignee_id"), nullableLong(rs, "resolved_by")),
				id).stream().findFirst();
	}

	/** The status gate of a take or an assignment. */
	int assignFromPool(long id, long assigneeId) {
		return jdbc.update("UPDATE tickets SET status = 'IN_PROGRESS', assignee_id = ? WHERE id = ? AND status = 'OPEN'", assigneeId, id);
	}

	/** The gate of a reassignment: the ticket is still in progress with the assignee the actor saw. */
	int reassign(long id, long fromUserId, long toUserId) {
		return jdbc.update("UPDATE tickets SET assignee_id = ? WHERE id = ? AND status = 'IN_PROGRESS' AND assignee_id = ?", toUserId, id,
				fromUserId);
	}

	/** The gate of a release: the ticket is still in progress with this assignee. */
	int release(long id, long assigneeId) {
		return jdbc.update("UPDATE tickets SET status = 'OPEN', assignee_id = NULL WHERE id = ? AND status = 'IN_PROGRESS' AND assignee_id = ?",
				id, assigneeId);
	}

	int resolve(long id, String resolution, long resolvedBy, Instant at) {
		return jdbc.update("""
				UPDATE tickets SET status = 'RESOLVED', resolution = ?, resolved_by = ?, resolved_at = ?
				WHERE id = ? AND status = 'IN_PROGRESS'
				""", resolution, resolvedBy, UtcTime.toLocalDateTime(at), id);
	}

	int cancel(long id, String reason, long cancelledBy, Instant at) {
		return jdbc.update("""
				UPDATE tickets SET status = 'CANCELLED', cancel_reason = ?, cancelled_by = ?, cancelled_at = ?
				WHERE id = ? AND status IN ('OPEN', 'IN_PROGRESS')
				""", reason, cancelledBy, UtcTime.toLocalDateTime(at), id);
	}

	int close(long id, long closedBy, Instant at) {
		return jdbc.update("UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_at = ? WHERE id = ? AND status = 'RESOLVED'", closedBy,
				UtcTime.toLocalDateTime(at), id);
	}

	void insertHistory(long ticketId, Long fromUserId, Long toUserId, long changedBy, String reason, Instant at) {
		jdbc.update("INSERT INTO ticket_assignment_history (ticket_id, from_user_id, to_user_id, changed_by, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				ticketId, fromUserId, toUserId, changedBy, reason, UtcTime.toLocalDateTime(at));
	}

	/** The username of a user, with a plain read, for the label of a log row. */
	String username(long userId) {
		return jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
	}

	// What the other modules start

	/**
	 * Locks every {@code IN_PROGRESS} ticket assigned to the user, in ascending id. Called with the user row
	 * locked, when the user is disabled or soft-deleted.
	 */
	List<Locked> lockInProgressOf(long userId) {
		return jdbc.query("""
				SELECT id, ticket_no, status, created_by, assignee_id, resolved_by FROM tickets
				WHERE assignee_id = ? AND status = 'IN_PROGRESS' ORDER BY id FOR UPDATE
				""", (rs, row) -> new Locked(rs.getLong("id"), rs.getString("ticket_no"), TicketStatus.of(rs.getString("status")),
				rs.getLong("created_by"), nullableLong(rs, "assignee_id"), nullableLong(rs, "resolved_by")), userId);
	}

	/**
	 * Locks every {@code OPEN} or {@code IN_PROGRESS} ticket linked to the item, in ascending id. The links
	 * are read with a shared lock, which reads what is committed now and needs only {@code SELECT} on the
	 * append-only table. Called after the item or its product is locked.
	 */
	List<Locked> lockLiveLinkedTo(long inventoryId) {
		return jdbc.query("""
				SELECT t.id, t.ticket_no, t.status, t.created_by, t.assignee_id, t.resolved_by
				FROM ticket_inventory ti JOIN tickets t ON t.id = ti.ticket_id
				WHERE ti.inventory_id = ? AND t.status IN ('OPEN', 'IN_PROGRESS')
				ORDER BY t.id
				FOR UPDATE OF t FOR SHARE OF ti
				""", (rs, row) -> new Locked(rs.getLong("id"), rs.getString("ticket_no"), TicketStatus.of(rs.getString("status")),
				rs.getLong("created_by"), nullableLong(rs, "assignee_id"), nullableLong(rs, "resolved_by")), inventoryId);
	}

	/** The ids of the {@code RESOLVED} tickets resolved before the time, oldest first, at most the limit. */
	List<Long> resolvedBefore(Instant time, int limit) {
		return jdbc.queryForList("SELECT id FROM tickets WHERE status = 'RESOLVED' AND resolved_at <= ? ORDER BY resolved_at, id LIMIT ?",
				Long.class, UtcTime.toLocalDateTime(time), limit);
	}

	int countResolved() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE status = 'RESOLVED'", Integer.class);
	}

	/** The ids of the {@code RESOLVED} tickets, oldest first, at most the limit. */
	List<Long> resolved(int limit) {
		return jdbc.queryForList("SELECT id FROM tickets WHERE status = 'RESOLVED' ORDER BY resolved_at, id LIMIT ?", Long.class, limit);
	}

	// Reading

	/**
	 * A page of one screen. The tickets with {@code source} {@code SYSTEM} come first in either direction,
	 * and inside each group the time of the screen orders the rows, then the id (see Ticket list order). The
	 * cursor key is the source, a bar, and the time.
	 */
	List<Listed> list(Filter filter, PageQuery page) {
		String time = "t." + timeColumn(filter.status());
		StringBuilder sql = new StringBuilder("SELECT t.id, t.ticket_no, t.type, t.source, t.title, t.created_at, t.status, t.closed_at, t.cancelled_at, ")
				.append(time).append(" AS sort_time, cu.id AS cu_id, cu.username AS cu_username, cu.is_system AS cu_system ")
				.append("FROM tickets t JOIN users cu ON cu.id = t.created_by WHERE t.status = ?");
		List<Object> args = new ArrayList<>();
		args.add(filter.status().name());
		if (filter.ownerId() != null) {
			switch (filter.status()) {
				case IN_PROGRESS -> condition(sql, args, "t.assignee_id = ?", filter.ownerId());
				case RESOLVED -> condition(sql, args, "t.resolved_by = ?", filter.ownerId());
				case CANCELLED -> condition(sql, args, "(t.created_by = ? OR t.assignee_id = ?)", filter.ownerId(), filter.ownerId());
				case CLOSED -> sql.append(" AND FALSE");
				case OPEN -> {
				}
			}
		}
		if (filter.source() != null) {
			condition(sql, args, "t.source = ?", filter.source());
		}
		if (filter.createdBy() != null) {
			condition(sql, args, "t.created_by = ?", filter.createdBy());
		}
		if (filter.assigneeId() != null) {
			condition(sql, args, "t.assignee_id = ?", filter.assigneeId());
		}
		if (filter.range() != null && filter.range().from() != null) {
			condition(sql, args, "t.created_at >= ?", filter.range().from());
		}
		if (filter.range() != null && filter.range().to() != null) {
			condition(sql, args, "t.created_at < ?", filter.range().to());
		}
		if (filter.ticketNo() != null) {
			condition(sql, args, "t.ticket_no = ?", filter.ticketNo());
		}
		if (filter.orderNo() != null) {
			condition(sql, args, "t.order_no = ?", filter.orderNo());
		}

		// SYSTEM sorts after MANUAL as text, so the source runs downward on the screen.
		boolean forward = page.direction() == Direction.NEXT;
		boolean upward = page.ascendingRead();
		if (page.cursor() != null) {
			String[] key = cursorKey(page.cursor().key());
			String sourceBeyond = forward ? "<" : ">";
			String beyond = upward ? ">" : "<";
			sql.append(" AND (t.source ").append(sourceBeyond).append(" ? OR (t.source = ? AND (").append(time).append(' ').append(beyond)
					.append(" ? OR (").append(time).append(" = ? AND t.id ").append(beyond).append(" ?))))");
			LocalDateTime edge = parseTime(key[1]);
			args.add(key[0]);
			args.add(key[0]);
			args.add(edge);
			args.add(edge);
			args.add(page.cursor().id());
		}
		String way = upward ? "ASC" : "DESC";
		sql.append(" ORDER BY t.source ").append(forward ? "DESC" : "ASC").append(", ").append(time).append(' ').append(way).append(", t.id ")
				.append(way).append(" LIMIT ?");
		args.add(page.fetchSize());
		return jdbc.query(sql.toString(), (rs, row) -> new Listed(rs.getLong("id"), rs.getString("ticket_no"), rs.getString("type"),
				rs.getString("source"), rs.getString("title"), new Person(rs.getLong("cu_id"), rs.getString("cu_username"), rs.getBoolean("cu_system")),
				time(rs, "created_at"), TicketStatus.of(rs.getString("status")), time(rs, "closed_at"), time(rs, "cancelled_at")), args.toArray());
	}

	/** The cursor key of a row of a list: its source, a bar, and the time of the screen. */
	static String cursorKeyOf(String source, Instant sortTime) {
		return source + "|" + UtcTime.toLocalDateTime(sortTime);
	}

	/** The time that orders the list of a screen. */
	static String timeColumn(TicketStatus status) {
		return switch (status) {
			case CLOSED -> "closed_at";
			case CANCELLED -> "cancelled_at";
			default -> "created_at";
		};
	}

	Optional<Detail> detail(long id) {
		return jdbc.query("""
				SELECT t.*,
				       cu.username AS cu_username, cu.is_system AS cu_system, au.username AS au_username, au.is_system AS au_system,
				       ru.username AS ru_username, ru.is_system AS ru_system, xu.username AS xu_username, xu.is_system AS xu_system,
				       nu.username AS nu_username, nu.is_system AS nu_system
				FROM tickets t
				""" + PEOPLE + " WHERE t.id = ?", (rs, row) -> new Detail(rs.getLong("id"), rs.getString("ticket_no"), rs.getString("type"),
				rs.getString("source"), TicketStatus.of(rs.getString("status")), rs.getString("title"), rs.getString("description"),
				rs.getString("order_no"), nullableLong(rs, "follows_ticket_id"), person(rs, "created_by", "cu"), time(rs, "created_at"),
				person(rs, "assignee_id", "au"), rs.getString("resolution"), person(rs, "resolved_by", "ru"), time(rs, "resolved_at"),
				person(rs, "closed_by", "xu"), time(rs, "closed_at"), rs.getString("cancel_reason"), person(rs, "cancelled_by", "nu"),
				time(rs, "cancelled_at"), nullableLong(rs, "resolved_by")), id).stream().findFirst();
	}

	Optional<Related> related(long id) {
		return jdbc.query("SELECT id, ticket_no, status, created_by, assignee_id, resolved_by FROM tickets WHERE id = ?", (rs, row) -> related(rs),
				id).stream().findFirst();
	}

	List<Related> followUps(long id) {
		return jdbc.query("SELECT id, ticket_no, status, created_by, assignee_id, resolved_by FROM tickets WHERE follows_ticket_id = ? ORDER BY id",
				(rs, row) -> related(rs), id);
	}

	List<LinkedItem> items(long ticketId) {
		return jdbc.query("""
				SELECT i.id, i.sku, i.name, i.deleted_at FROM ticket_inventory ti JOIN inventory i ON i.id = ti.inventory_id
				WHERE ti.ticket_id = ? ORDER BY i.name, i.id
				""", (rs, row) -> new LinkedItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getObject("deleted_at") != null),
				ticketId);
	}

	List<HistoryRow> history(long ticketId) {
		return jdbc.query("""
				SELECT h.from_user_id, fu.username AS fu_username, fu.is_system AS fu_system,
				       h.to_user_id, tu.username AS tu_username, tu.is_system AS tu_system,
				       h.changed_by, bu.username AS bu_username, bu.is_system AS bu_system, h.reason, h.created_at
				FROM ticket_assignment_history h
				LEFT JOIN users fu ON fu.id = h.from_user_id
				LEFT JOIN users tu ON tu.id = h.to_user_id
				JOIN users bu ON bu.id = h.changed_by
				WHERE h.ticket_id = ? ORDER BY h.created_at, h.id
				""", (rs, row) -> new HistoryRow(person(rs, "from_user_id", "fu"), person(rs, "to_user_id", "tu"), person(rs, "changed_by", "bu"),
				rs.getString("reason"), time(rs, "created_at")), ticketId);
	}

	Optional<String> itemNameOf(long inventoryId) {
		return itemName(inventoryId);
	}

	Optional<String> productName(long productId) {
		return jdbc.queryForList("SELECT name FROM products WHERE id = ?", String.class, productId).stream().findFirst();
	}

	// Helpers

	private static void condition(StringBuilder sql, List<Object> args, String condition, Object... values) {
		sql.append(" AND ").append(condition);
		args.addAll(List.of(values));
	}

	private static String[] cursorKey(String key) {
		int bar = key.indexOf('|');
		if (bar < 0) {
			throw ApiException.invalid("cursor", "is not a valid cursor");
		}
		String source = key.substring(0, bar);
		if (!"SYSTEM".equals(source) && !"MANUAL".equals(source)) {
			throw ApiException.invalid("cursor", "is not a valid cursor");
		}
		return new String[] { source, key.substring(bar + 1) };
	}

	private static LocalDateTime parseTime(String text) {
		try {
			return LocalDateTime.parse(text);
		}
		catch (DateTimeParseException e) {
			throw ApiException.invalid("cursor", "is not a valid cursor");
		}
	}

	private static Related related(ResultSet rs) throws SQLException {
		return new Related(rs.getLong("id"), rs.getString("ticket_no"), TicketStatus.of(rs.getString("status")), rs.getLong("created_by"),
				nullableLong(rs, "assignee_id"), nullableLong(rs, "resolved_by"));
	}

	private static Person person(ResultSet rs, String idColumn, String alias) throws SQLException {
		Long id = nullableLong(rs, idColumn);
		if (id == null) {
			return null;
		}
		return new Person(id, rs.getString(alias + "_username"), rs.getBoolean(alias + "_system"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Instant time(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value == null ? null : UtcTime.toInstant(value);
	}

}
