package com.opsflow.order;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
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
 * The SQL of placing an order: it reads {@code products} and {@code inventory} and writes {@code orders},
 * {@code order_items}, {@code inventory}, {@code inventory_transactions}, and {@code order_attempts}. It is
 * kept in this module, apart from the warehouse, so that the lock order of the order can be read in one
 * place: {@code products} (shared, ascending id), then {@code orders}, then {@code inventory} (exclusive,
 * ascending id). A method that locks says so.
 */
@Repository
class OrderRepository {

	/** A product as the order reads it. */
	record ProductRow(long id, long inventoryId, String name, BigDecimal price, String status) {

		boolean listed() {
			return "LISTED".equals(status);
		}
	}

	/** An order that was placed, with its lines, as the answer shows it. */
	record PlacedOrder(long id, String orderNo, BigDecimal totalAmount, int itemCount, Instant createdAt, List<PlacedLine> lines) {
	}

	/** A line of an order, frozen when it was placed. */
	record PlacedLine(long productId, String sku, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
	}

	private final JdbcTemplate jdbc;

	OrderRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Reads a product under a shared lock: a listing, a delisting, or a deletion waits until the order ends. */
	Optional<ProductRow> shareProduct(long id) {
		return jdbc.query("SELECT id, inventory_id, name, price, status FROM products WHERE id = ? FOR SHARE",
				(rs, row) -> new ProductRow(rs.getLong("id"), rs.getLong("inventory_id"), rs.getString("name"), rs.getBigDecimal("price"),
						rs.getString("status")),
				id).stream().findFirst();
	}

	/**
	 * The SKU of an item, with a plain read. Never a locking read here: a shared lock on the row would later
	 * have to become the exclusive lock of the deduction, and two orders could each hold what the other needs.
	 */
	String skuOf(long inventoryId) {
		return jdbc.queryForObject("SELECT sku FROM inventory WHERE id = ?", String.class, inventoryId);
	}

	/** The current name of a product, with a plain read, for the record of a refused attempt. */
	Optional<String> productName(long id) {
		return jdbc.queryForList("SELECT name FROM products WHERE id = ?", String.class, id).stream().findFirst();
	}

	/**
	 * Inserts the order. {@code uk_orders_user_idempotency} refuses a key the user already used, also against
	 * an order that has not committed yet (the insert waits for it), and {@code uk_orders_order_no} a number
	 * used before.
	 */
	long insertOrder(String orderNo, long userId, BigDecimal totalAmount, int itemCount, String idempotencyKey, String remark,
			Instant createdAt) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO orders (order_no, user_id, total_amount, item_count, idempotency_key, remark, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, orderNo);
			statement.setLong(2, userId);
			statement.setBigDecimal(3, totalAmount);
			statement.setInt(4, itemCount);
			statement.setString(5, idempotencyKey);
			statement.setString(6, remark);
			statement.setObject(7, UtcTime.toLocalDateTime(createdAt));
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	void insertOrderItem(long orderId, PlacedLine line) {
		jdbc.update("""
				INSERT INTO order_items (order_id, product_id, product_sku, product_name, unit_price, quantity, subtotal)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", orderId, line.productId(), line.sku(), line.name(), line.unitPrice(), line.quantity(), line.subtotal());
	}

	/** Reads the available stock of an item and holds its row until the transaction ends. */
	Optional<Integer> lockStock(long inventoryId) {
		return jdbc.queryForList("SELECT available_quantity FROM inventory WHERE id = ? FOR UPDATE", Integer.class, inventoryId)
				.stream().findFirst();
	}

	/** Called with the row locked: the sale takes the stock and counts it as sold. */
	void deduct(long inventoryId, int quantity) {
		jdbc.update("UPDATE inventory SET available_quantity = available_quantity - ?, sold_quantity = sold_quantity + ? WHERE id = ?",
				quantity, quantity, inventoryId);
	}

	/** The {@code SALE} movement of one item, with the snapshots read under the lock. */
	void insertSale(long inventoryId, long orderId, long operatorId, int quantity, int before, int after, Instant at) {
		jdbc.update("""
				INSERT INTO inventory_transactions (inventory_id, order_id, operator_id, type, quantity, available_before, available_after, created_at)
				VALUES (?, ?, ?, 'SALE', ?, ?, ?, ?)
				""", inventoryId, orderId, operatorId, quantity, before, after, UtcTime.toLocalDateTime(at));
	}

	/** One product line of an attempt. {@code orderId} is set only for a completed attempt. */
	void insertAttempt(String orderNo, long userId, long productId, String productName, Long orderId, int quantity, String result,
			int stockDeducted, Instant at) {
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO order_attempts (order_no, user_id, product_id, product_name, order_id, quantity, result, stock_deducted, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""");
			statement.setString(1, orderNo);
			statement.setLong(2, userId);
			statement.setLong(3, productId);
			statement.setString(4, productName);
			if (orderId == null) {
				statement.setNull(5, Types.BIGINT);
			}
			else {
				statement.setLong(5, orderId);
			}
			statement.setInt(6, quantity);
			statement.setString(7, result);
			statement.setInt(8, stockDeducted);
			statement.setObject(9, UtcTime.toLocalDateTime(at));
			return statement;
		});
	}

	/** The order a user placed with an idempotency key, with its lines, or empty. A plain read. */
	Optional<PlacedOrder> findByKey(long userId, String idempotencyKey) {
		return jdbc.query("SELECT id, order_no, total_amount, item_count, created_at FROM orders WHERE user_id = ? AND idempotency_key = ?",
				(rs, row) -> new PlacedOrder(rs.getLong("id"), rs.getString("order_no"), rs.getBigDecimal("total_amount"),
						rs.getInt("item_count"), UtcTime.toInstant(rs.getObject("created_at", LocalDateTime.class)), List.of()),
				userId, idempotencyKey).stream().findFirst().map(order -> new PlacedOrder(order.id(), order.orderNo(),
						order.totalAmount(), order.itemCount(), order.createdAt(), linesOf(order.id())));
	}

	private List<PlacedLine> linesOf(long orderId) {
		return jdbc.query("""
				SELECT product_id, product_sku, product_name, unit_price, quantity, subtotal
				FROM order_items WHERE order_id = ? ORDER BY product_id
				""", (rs, row) -> new PlacedLine(rs.getLong("product_id"), rs.getString("product_sku"), rs.getString("product_name"),
				rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("subtotal")), orderId);
	}

}
