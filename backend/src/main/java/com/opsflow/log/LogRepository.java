package com.opsflow.log;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.time.DayRanges.Range;
import com.opsflow.common.time.UtcTime;

/**
 * The reads of the two logs (see Logs in docs/database/database-design.md): the listing records and the order
 * records of the product log, the item records and the stock movements of the warehouse log, the Deleted
 * items module, and the product picker. Nothing here locks or writes: the records are append-only, and a
 * list shows what had committed when it was read.
 *
 * A log is read by key on the creation time and then the id, in the direction the person chose, with the
 * product or item index when one is chosen and the time index otherwise.
 */
@Repository
class LogRepository {

	/** A listing or a delisting, with the name and price at that moment. */
	record ListingRecord(long id, Instant at, long productId, long inventoryId, String action, String name, BigDecimal price, String by) {
	}

	/** A line of an attempt to order, placed or refused. */
	record OrderRecord(long id, Instant at, long productId, String productName, String orderNo, String orderedBy, int quantity,
			String result, int stockDeducted) {
	}

	/** An item added or deleted, with the name, SKU, and quantity at that moment. */
	record ItemRecord(long id, Instant at, long inventoryId, String action, String sku, String name, int quantity, String by) {
	}

	/** A stock movement. {@code amount} is signed: {@code +} into the stock, {@code -} out of it. */
	record StockMovement(long id, Instant at, long inventoryId, String sku, String itemName, String type, String orderNo, String reason,
			int amount, String by) {
	}

	/** A deleted item, and its product if it ever had one. */
	record DeletedItem(long id, String sku, String name, Instant deletedAt, String deletedBy, Long productId) {
	}

	/**
	 * A row of {@code operation_logs} as the audit page shows it. {@code operatorId} and {@code by} are null for
	 * an action of the system. The data is the JSON text of the row.
	 */
	record AuditRecord(long id, Instant at, String category, String action, Long operatorId, String by, String targetType, long targetId,
			String beforeData, String afterData, String ipAddress, String remark) {
	}

	/** A product of the picker: every product ever listed, except those of deleted items. */
	record ProductOption(long id, String name, String sku) {
	}

	/** The character that escapes {@code %}, {@code _}, and itself in a search. */
	private static final char LIKE_ESCAPE = '!';

	private final JdbcTemplate jdbc;

	LogRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// The product log

	List<ListingRecord> listingRecords(Long productId, Range range, PageQuery page) {
		Query q = new Query("""
				SELECT e.id, e.created_at, e.product_id, e.inventory_id, e.action, e.name, e.price, u.username
				FROM product_listing_events e JOIN users u ON u.id = e.operator_id
				WHERE 1 = 1""");
		q.equal("e.product_id", productId);
		q.during("e.created_at", range);
		return jdbc.query(q.byTime("e.created_at", "e.id", page), (rs, row) -> new ListingRecord(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)),
				rs.getLong("product_id"), rs.getLong("inventory_id"), rs.getString("action"), rs.getString("name"), rs.getBigDecimal("price"),
				rs.getString("username")), q.args());
	}

	/**
	 * Read through {@code idx_order_attempts_product_created}, {@code idx_order_attempts_user_created}, or
	 * {@code idx_order_attempts_created}.
	 *
	 * @param userId the user who ordered, or null for anyone
	 * @param orderNo one whole order number, or null; it is used alone, except for the caller's own records
	 */
	List<OrderRecord> orderRecords(Long productId, Long userId, String orderNo, Range range, PageQuery page) {
		Query q = new Query("""
				SELECT a.id, a.created_at, a.product_id, a.product_name, a.order_no, u.username, a.quantity, a.result, a.stock_deducted
				FROM order_attempts a JOIN users u ON u.id = a.user_id
				WHERE 1 = 1""");
		q.equal("a.product_id", productId);
		q.equal("a.user_id", userId);
		q.equal("a.order_no", orderNo);
		q.during("a.created_at", range);
		RowMapper<OrderRecord> mapper = (rs, row) -> new OrderRecord(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)),
				rs.getLong("product_id"), rs.getString("product_name"), rs.getString("order_no"), rs.getString("username"),
				rs.getInt("quantity"), rs.getString("result"), rs.getInt("stock_deducted"));
		return jdbc.query(q.byTime("a.created_at", "a.id", page), mapper, q.args());
	}

	// The audit page

	/**
	 * The rows of one category of the audit page, read through {@code (category, created_at)}, or through
	 * {@code (operator_id, created_at)} when one user is chosen.
	 *
	 * @param action one action of the category, or null for all
	 * @param userId the user who acted, or null for anyone
	 * @param systemOnly only the actions of the system (no operator)
	 * @param ticketNo the number of the ticket the rows are about, or null for every ticket
	 */
	List<AuditRecord> auditRecords(String category, String action, Long userId, boolean systemOnly, Long aboutUserId, String ticketNo,
			Range range, PageQuery page) {
		Query q = new Query("""
				SELECT l.id, l.created_at, l.category, l.action, l.operator_id, u.username, l.target_type, l.target_id,
				       CAST(l.before_data AS CHAR) AS before_data, CAST(l.after_data AS CHAR) AS after_data, l.ip_address, l.remark
				FROM operation_logs l LEFT JOIN users u ON u.id = l.operator_id
				WHERE 1 = 1""");
		q.equal("l.category", category);
		q.equal("l.action", action);
		q.equal("l.operator_id", userId);
		if (systemOnly) {
			q.condition("l.operator_id IS NULL");
		}
		if (aboutUserId != null) {
			// The rows whose target is this user, through idx_operation_logs_target.
			q.condition("l.target_type = 'USER' AND l.target_id = ?", aboutUserId);
		}
		if (ticketNo != null) {
			// The rows whose target is this ticket, through the unique key of the number and idx_operation_logs_target.
			// A number that no ticket has finds no row.
			q.condition("l.target_type = 'TICKET' AND l.target_id = (SELECT t.id FROM tickets t WHERE t.ticket_no = ?)", ticketNo);
		}
		q.during("l.created_at", range);
		RowMapper<AuditRecord> mapper = (rs, row) -> {
			long operator = rs.getLong("operator_id");
			Long operatorId = rs.wasNull() ? null : operator;
			return new AuditRecord(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)), rs.getString("category"),
					rs.getString("action"), operatorId, rs.getString("username"), rs.getString("target_type"), rs.getLong("target_id"),
					rs.getString("before_data"), rs.getString("after_data"), rs.getString("ip_address"), rs.getString("remark"));
		};
		return jdbc.query(q.byTime("l.created_at", "l.id", page), mapper, q.args());
	}

	/**
	 * The Orders area of the audit page: the lines of every attempt, the same rows as the order records, filtered
	 * by the result and the user who ordered, read through {@code idx_order_attempts_created} or
	 * {@code idx_order_attempts_user_created}.
	 */
	List<OrderRecord> auditOrderRecords(String result, Long userId, Range range, PageQuery page) {
		Query q = new Query("""
				SELECT a.id, a.created_at, a.product_id, a.product_name, a.order_no, u.username, a.quantity, a.result, a.stock_deducted
				FROM order_attempts a JOIN users u ON u.id = a.user_id
				WHERE 1 = 1""");
		q.equal("a.result", result);
		q.equal("a.user_id", userId);
		q.during("a.created_at", range);
		RowMapper<OrderRecord> mapper = (rs, row) -> new OrderRecord(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)),
				rs.getLong("product_id"), rs.getString("product_name"), rs.getString("order_no"), rs.getString("username"),
				rs.getInt("quantity"), rs.getString("result"), rs.getInt("stock_deducted"));
		return jdbc.query(q.byTime("a.created_at", "a.id", page), mapper, q.args());
	}

	// The warehouse log

	List<ItemRecord> itemRecords(Long inventoryId, Range range, PageQuery page) {
		Query q = new Query("""
				SELECT e.id, e.created_at, e.inventory_id, e.action, e.sku, e.name, e.quantity, u.username
				FROM inventory_item_events e JOIN users u ON u.id = e.operator_id
				WHERE 1 = 1""");
		q.equal("e.inventory_id", inventoryId);
		q.during("e.created_at", range);
		return jdbc.query(q.byTime("e.created_at", "e.id", page), (rs, row) -> new ItemRecord(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)),
				rs.getLong("inventory_id"), rs.getString("action"), rs.getString("sku"), rs.getString("name"), rs.getInt("quantity"),
				rs.getString("username")), q.args());
	}

	/** @param orderNo one whole order number, or null; it finds the {@code SALE} rows of that order and is used alone */
	List<StockMovement> stockMovements(Long inventoryId, String orderNo, Range range, PageQuery page) {
		Query q = new Query("""
				SELECT t.id, t.created_at, t.inventory_id, i.sku, i.name, t.type, o.order_no, t.reason, t.quantity, u.username
				FROM inventory_transactions t
				JOIN inventory i ON i.id = t.inventory_id
				JOIN users u ON u.id = t.operator_id
				LEFT JOIN orders o ON o.id = t.order_id
				WHERE 1 = 1""");
		q.equal("t.inventory_id", inventoryId);
		q.equal("o.order_no", orderNo);
		q.during("t.created_at", range);
		return jdbc.query(q.byTime("t.created_at", "t.id", page), (rs, row) -> {
			String type = rs.getString("type");
			int quantity = rs.getInt("quantity");
			int amount = "RESTOCK".equals(type) || "ADJUST_IN".equals(type) ? quantity : -quantity;
			return new StockMovement(rs.getLong("id"), time(rs.getObject("created_at", LocalDateTime.class)), rs.getLong("inventory_id"),
					rs.getString("sku"), rs.getString("name"), type, rs.getString("order_no"), rs.getString("reason"), amount,
					rs.getString("username"));
		}, q.args());
	}

	/**
	 * The deleted items, newest deletion first, with a search of a part of the name or the SKU. The warehouse
	 * table is small and has no index for this list (see Logs).
	 */
	List<DeletedItem> deletedItems(String search, PageQuery page) {
		Query q = new Query("""
				SELECT i.id, i.sku, i.name, i.deleted_at, u.username, p.id AS product_id
				FROM inventory i
				JOIN users u ON u.id = i.deleted_by
				LEFT JOIN products p ON p.inventory_id = i.id
				WHERE i.deleted_at IS NOT NULL""");
		if (search != null) {
			String pattern = "%" + likeLiteral(search) + "%";
			q.condition("(i.name LIKE ? ESCAPE '" + LIKE_ESCAPE + "' OR i.sku LIKE ? ESCAPE '" + LIKE_ESCAPE + "')", pattern, pattern);
		}
		return jdbc.query(q.byTime("i.deleted_at", "i.id", page), (rs, row) -> new DeletedItem(rs.getLong("id"), rs.getString("sku"),
				rs.getString("name"), time(rs.getObject("deleted_at", LocalDateTime.class)), rs.getString("username"),
				rs.getObject("product_id", Long.class)), q.args());
	}

	/** The product picker, by name and then id, with a search of a part of the name. */
	List<ProductOption> productOptions(String search, PageQuery page) {
		Query q = new Query("""
				SELECT p.id, p.name, i.sku
				FROM products p JOIN inventory i ON i.id = p.inventory_id
				WHERE i.deleted_at IS NULL""");
		if (search != null) {
			q.condition("p.name LIKE ? ESCAPE '" + LIKE_ESCAPE + "'", "%" + likeLiteral(search) + "%");
		}
		return jdbc.query(q.byKey("p.name", "p.id", page, false), (rs, row) -> new ProductOption(rs.getLong("id"), rs.getString("name"),
				rs.getString("sku")), q.args());
	}

	/** The key of a cursor on a time column: the UTC wall time as ISO text. */
	static String timeKey(Instant at) {
		return UtcTime.toLocalDateTime(at).toString();
	}

	private static Instant time(LocalDateTime utc) {
		return UtcTime.toInstant(utc);
	}

	private static String likeLiteral(String text) {
		StringBuilder escaped = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	/** A query built from its conditions, with the arguments in the order of its placeholders. */
	private static final class Query {

		private final StringBuilder sql;
		private final List<Object> args = new ArrayList<>();

		Query(String select) {
			this.sql = new StringBuilder(select);
		}

		void equal(String column, Object value) {
			if (value != null) {
				condition(column + " = ?", value);
			}
		}

		void during(String column, Range range) {
			if (range.from() != null) {
				condition(column + " >= ?", range.from());
			}
			if (range.to() != null) {
				condition(column + " < ?", range.to());
			}
		}

		void condition(String condition, Object... values) {
			sql.append(" AND ").append(condition);
			args.addAll(List.of(values));
		}

		/** Paged by key on a time column, whose cursor key is the ISO text of a UTC wall time. */
		String byTime(String key, String id, PageQuery page) {
			return byKey(key, id, page, true);
		}

		String byKey(String key, String id, PageQuery page, boolean timeKey) {
			boolean upward = page.ascendingRead();
			if (page.cursor() != null) {
				Object edge = page.cursor().key();
				if (timeKey) {
					try {
						edge = LocalDateTime.parse(page.cursor().key());
					}
					catch (DateTimeParseException e) {
						throw ApiException.invalid("cursor", "is not a valid cursor");
					}
				}
				String beyond = upward ? ">" : "<";
				sql.append(" AND (").append(key).append(' ').append(beyond).append(" ? OR (").append(key).append(" = ? AND ").append(id)
						.append(' ').append(beyond).append(" ?))");
				args.add(edge);
				args.add(edge);
				args.add(page.cursor().id());
			}
			String way = upward ? "ASC" : "DESC";
			sql.append(" ORDER BY ").append(key).append(' ').append(way).append(", ").append(id).append(' ').append(way).append(" LIMIT ?");
			args.add(page.fetchSize());
			return sql.toString();
		}

		Object[] args() {
			return args.toArray();
		}
	}

}
