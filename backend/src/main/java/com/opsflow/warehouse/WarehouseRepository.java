package com.opsflow.warehouse;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.time.UtcTime;

/**
 * The SQL of the warehouse: {@code inventory}, {@code products}, {@code inventory_transactions},
 * {@code inventory_item_events}, and {@code product_listing_events}, and the three lists. A method that
 * locks says so; the callers lock in the order of Concurrency by Module, {@code products} and then
 * {@code inventory}.
 */
@Repository
class WarehouseRepository {

	/** An item as a change reads it. {@code deleted} is true when {@code deleted_at} is set. */
	record ItemRow(long id, String sku, String name, String imagePath, int availableQuantity, boolean deleted) {
	}

	/** A product as a change reads it. */
	record ProductRow(long id, long inventoryId, String name, BigDecimal price, String status) {

		boolean listed() {
			return "LISTED".equals(status);
		}
	}

	/** A row of the stock page. */
	record ListedItem(long id, String sku, String name, String imagePath, int availableQuantity, boolean listed) {
	}

	/** A row of the product page. */
	record ShopProduct(long id, String name, BigDecimal price, String imagePath) {
	}

	/** A row of the catalog: an item that can be listed. */
	record ListableItem(long inventoryId, String sku, String name, String imagePath, int availableQuantity) {
	}

	/** The character that escapes {@code %}, {@code _}, and itself in a search. */
	private static final char LIKE_ESCAPE = '!';

	private static final String ITEM_COLUMNS = "id, sku, name, image_url, available_quantity, deleted_at IS NOT NULL AS deleted FROM inventory";
	private static final String PRODUCT_COLUMNS = "id, inventory_id, name, price, status FROM products";

	private static final RowMapper<ItemRow> ITEM = (rs, row) -> new ItemRow(rs.getLong("id"), rs.getString("sku"),
			rs.getString("name"), rs.getString("image_url"), rs.getInt("available_quantity"), rs.getBoolean("deleted"));
	private static final RowMapper<ProductRow> PRODUCT = (rs, row) -> new ProductRow(rs.getLong("id"),
			rs.getLong("inventory_id"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("status"));

	private final JdbcTemplate jdbc;

	WarehouseRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Items

	/** Inserts a new item. The unique keys refuse a repeated SKU or a name in use, with a duplicate key. */
	long insertItem(String sku, String name, String imagePath, int availableQuantity, long createdBy) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO inventory (sku, name, image_url, available_quantity, created_by) VALUES (?, ?, ?, ?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, sku);
			statement.setString(2, name);
			statement.setString(3, imagePath);
			statement.setInt(4, availableQuantity);
			statement.setLong(5, createdBy);
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	/** Reads an item and holds its row until the transaction ends. */
	Optional<ItemRow> lockItem(long id) {
		return jdbc.query("SELECT " + ITEM_COLUMNS + " WHERE id = ? FOR UPDATE", ITEM, id).stream().findFirst();
	}

	/** Reads an item under a shared lock: a change of its stock waits until the transaction ends. */
	Optional<ItemRow> shareItem(long id) {
		return jdbc.query("SELECT " + ITEM_COLUMNS + " WHERE id = ? FOR SHARE", ITEM, id).stream().findFirst();
	}

	/** Reads an item with a plain read, for the answer of a change that already holds or released its lock. */
	Optional<ItemRow> findItem(long id) {
		return jdbc.query("SELECT " + ITEM_COLUMNS + " WHERE id = ?", ITEM, id).stream().findFirst();
	}

	/** Called with the row locked. */
	void updateItem(long id, String name, String imagePath) {
		jdbc.update("UPDATE inventory SET name = ?, image_url = ? WHERE id = ?", name, imagePath, id);
	}

	/** Called with the row locked. The stock is left as it is. */
	void softDeleteItem(long id, Instant at, long deletedBy) {
		jdbc.update("UPDATE inventory SET deleted_at = ?, deleted_by = ? WHERE id = ?", UtcTime.toLocalDateTime(at), deletedBy, id);
	}

	/** A stock movement. The snapshots are taken from the row the caller holds. */
	long insertTransaction(long inventoryId, String type, int quantity, int before, int after, String reason, long operatorId) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO inventory_transactions (inventory_id, operator_id, type, quantity, available_before, available_after, reason)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, inventoryId);
			statement.setLong(2, operatorId);
			statement.setString(3, type);
			statement.setInt(4, quantity);
			statement.setInt(5, before);
			statement.setInt(6, after);
			statement.setString(7, reason);
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	void insertItemEvent(long inventoryId, String action, String sku, String name, int quantity, long operatorId) {
		jdbc.update("INSERT INTO inventory_item_events (inventory_id, action, sku, name, quantity, operator_id) VALUES (?, ?, ?, ?, ?, ?)",
				inventoryId, action, sku, name, quantity, operatorId);
	}

	// Products

	/** Reads the product of an item, if it has one, and holds its row until the transaction ends. */
	Optional<ProductRow> lockProductOfItem(long inventoryId) {
		return jdbc.query("SELECT " + PRODUCT_COLUMNS + " WHERE inventory_id = ? FOR UPDATE", PRODUCT, inventoryId).stream().findFirst();
	}

	/** Reads a product and holds its row until the transaction ends. */
	Optional<ProductRow> lockProduct(long id) {
		return jdbc.query("SELECT " + PRODUCT_COLUMNS + " WHERE id = ? FOR UPDATE", PRODUCT, id).stream().findFirst();
	}

	/** The first listing of an item. {@code uk_products_inventory} refuses a second product of the item. */
	long insertProduct(long inventoryId, String name, BigDecimal price, Instant listedAt, long listedBy) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO products (inventory_id, name, price, status, listed_at, listed_by) VALUES (?, ?, ?, 'LISTED', ?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, inventoryId);
			statement.setString(2, name);
			statement.setBigDecimal(3, price);
			statement.setObject(4, UtcTime.toLocalDateTime(listedAt));
			statement.setLong(5, listedBy);
			return statement;
		}, key);
		return key.getKey().longValue();
	}

	/** A later listing, called with the row locked; the status gate repeats what the lock showed. */
	int relist(long id, String name, BigDecimal price, Instant listedAt, long listedBy) {
		return jdbc.update("""
				UPDATE products SET status = 'LISTED', name = ?, price = ?, listed_at = ?, listed_by = ?, delisted_at = NULL, delisted_by = NULL
				WHERE id = ? AND status = 'UNLISTED'
				""", name, price, UtcTime.toLocalDateTime(listedAt), listedBy, id);
	}

	/** Called with the row locked; the status gate repeats what the lock showed. */
	int delist(long id, Instant delistedAt, long delistedBy) {
		return jdbc.update("UPDATE products SET status = 'UNLISTED', delisted_at = ?, delisted_by = ? WHERE id = ? AND status = 'LISTED'",
				UtcTime.toLocalDateTime(delistedAt), delistedBy, id);
	}

	void insertListingEvent(long productId, long inventoryId, String action, String name, BigDecimal price, long operatorId) {
		jdbc.update("INSERT INTO product_listing_events (product_id, inventory_id, action, name, price, operator_id) VALUES (?, ?, ?, ?, ?, ?)",
				productId, inventoryId, action, name, price, operatorId);
	}

	// Lists. Each is read by key on the name and then the id; the name compares without regard to case.

	List<ListedItem> listItems(String namePart, PageQuery page) {
		List<Object> args = new ArrayList<>();
		String sql = """
				SELECT i.id, i.sku, i.name, i.image_url, i.available_quantity,
				       EXISTS (SELECT 1 FROM products p WHERE p.inventory_id = i.id AND p.status = 'LISTED') AS listed
				FROM inventory i
				WHERE i.deleted_at IS NULL
				""" + search("i.name", namePart, args) + keyed("i.name", "i.id", page, args);
		return jdbc.query(sql, (rs, row) -> new ListedItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
				rs.getString("image_url"), rs.getInt("available_quantity"), rs.getBoolean("listed")), args.toArray());
	}

	List<ShopProduct> listProducts(String namePart, PageQuery page) {
		List<Object> args = new ArrayList<>();
		String sql = """
				SELECT p.id, p.name, p.price, i.image_url
				FROM products p JOIN inventory i ON i.id = p.inventory_id
				WHERE p.status = 'LISTED'
				""" + search("p.name", namePart, args) + keyed("p.name", "p.id", page, args);
		return jdbc.query(sql, (rs, row) -> new ShopProduct(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price"),
				rs.getString("image_url")), args.toArray());
	}

	List<ListableItem> listListable(String namePart, PageQuery page) {
		List<Object> args = new ArrayList<>();
		String sql = """
				SELECT i.id, i.sku, i.name, i.image_url, i.available_quantity
				FROM inventory i
				WHERE i.deleted_at IS NULL
				  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.inventory_id = i.id AND p.status = 'LISTED')
				""" + search("i.name", namePart, args) + keyed("i.name", "i.id", page, args);
		return jdbc.query(sql, (rs, row) -> new ListableItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
				rs.getString("image_url"), rs.getInt("available_quantity")), args.toArray());
	}

	private static String search(String column, String part, List<Object> args) {
		if (part == null) {
			return "";
		}
		args.add("%" + likeLiteral(part) + "%");
		return " AND " + column + " LIKE ? ESCAPE '" + LIKE_ESCAPE + "'";
	}

	private static String keyed(String key, String id, PageQuery page, List<Object> args) {
		StringBuilder sql = new StringBuilder();
		boolean upward = page.ascendingRead();
		if (page.cursor() != null) {
			String beyond = upward ? ">" : "<";
			sql.append(" AND (").append(key).append(' ').append(beyond).append(" ? OR (").append(key).append(" = ? AND ")
					.append(id).append(' ').append(beyond).append(" ?))");
			args.add(page.cursor().key());
			args.add(page.cursor().key());
			args.add(page.cursor().id());
		}
		String way = upward ? "ASC" : "DESC";
		sql.append(" ORDER BY ").append(key).append(' ').append(way).append(", ").append(id).append(' ').append(way).append(" LIMIT ?");
		args.add(page.fetchSize());
		return sql.toString();
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

}
