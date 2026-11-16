package com.opsflow.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opsflow.support.MySqlIntegrationTest;

/**
 * T-13: the limit of every text field equals the length of its database column. The table lists, for
 * each constant of {@link Limits}, the columns it stands for; the length of each column is read from the
 * database that Flyway built, so a migration that changes a length fails here until the constant follows.
 * A {@code TEXT} column has no length to match: its limit is chosen by the design, and the test checks
 * that it fits the column at four bytes a character ({@code utf8mb4}).
 */
class LimitsMatchSchemaTest extends MySqlIntegrationTest {

	private record Column(String table, String column) {
	}

	/** The {@code TEXT} columns, whose limit must fit and need not be equal. */
	private static final Set<String> TEXT_COLUMNS = Set.of("tickets.description", "tickets.resolution");

	private static List<Column> columnsOf(String name) {
		return switch (name) {
			case "USERNAME" -> List.of(new Column("users", "username"));
			case "EMAIL" -> List.of(new Column("users", "email"), new Column("email_verification_codes", "email"));
			case "ROLE_CODE" -> List.of(new Column("roles", "code"));
			case "ROLE_NAME" -> List.of(new Column("roles", "name"));
			case "ROLE_DESCRIPTION" -> List.of(new Column("roles", "description"));
			case "SKU" -> List.of(new Column("inventory", "sku"), new Column("inventory_item_events", "sku"));
			case "ITEM_NAME" -> List.of(new Column("inventory", "name"), new Column("products", "name"),
					new Column("order_items", "product_name"), new Column("order_attempts", "product_name"));
			case "ORDER_NO" -> List.of(new Column("orders", "order_no"), new Column("order_attempts", "order_no"),
					new Column("tickets", "order_no"));
			case "IDEMPOTENCY_KEY" -> List.of(new Column("orders", "idempotency_key"));
			case "ORDER_REMARK" -> List.of(new Column("orders", "remark"));
			case "TICKET_NO" -> List.of(new Column("tickets", "ticket_no"));
			case "TICKET_TITLE" -> List.of(new Column("tickets", "title"));
			case "REASON" -> List.of(new Column("inventory_transactions", "reason"), new Column("tickets", "cancel_reason"),
					new Column("ticket_assignment_history", "reason"));
			case "TICKET_DESCRIPTION" -> List.of(new Column("tickets", "description"));
			case "TICKET_RESOLUTION" -> List.of(new Column("tickets", "resolution"));
			default -> throw new AssertionError("Limits." + name + " has no column listed in this test: add it to columnsOf");
		};
	}

	@Test
	void everyLimitEqualsTheLengthOfItsColumns() throws IllegalAccessException {
		List<String> mismatches = new ArrayList<>();
		Set<String> checked = new HashSet<>();
		for (Field field : Limits.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
				continue;
			}
			int limit = field.getInt(null);
			for (Column column : columnsOf(field.getName())) {
				String name = column.table() + "." + column.column();
				checked.add(name);
				if (TEXT_COLUMNS.contains(name)) {
					Long bytes = root().queryForObject(
							"SELECT character_octet_length FROM information_schema.columns WHERE table_schema = 'opsflow' AND table_name = ? AND column_name = ? AND data_type = 'text'",
							Long.class, column.table(), column.column());
					if (bytes == null || (long) limit * 4 > bytes) {
						mismatches.add("Limits." + field.getName() + " is " + limit + " characters but the TEXT column " + name + " holds " + bytes + " bytes");
					}
					continue;
				}
				Long length = root().queryForObject(
						"SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = 'opsflow' AND table_name = ? AND column_name = ?",
						Long.class, column.table(), column.column());
				if (length == null || length != limit) {
					mismatches.add("Limits." + field.getName() + " is " + limit + " but " + column.table() + "." + column.column() + " is " + length);
				}
			}
		}

		assertThat(mismatches).isEmpty();
		assertThat(checked).hasSizeGreaterThanOrEqualTo(20);
	}

}
