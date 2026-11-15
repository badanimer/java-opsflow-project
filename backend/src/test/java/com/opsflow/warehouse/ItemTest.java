package com.opsflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Adding, listing, editing, and deleting warehouse items (T-39, T-41), through HTTP against the MySQL
 * container. The rules are in Warehouse items and Valid quantities in docs/database/database-design.md.
 */
class ItemTest extends WarehouseIntegrationSupport {

	long mary;

	@BeforeEach
	void people() {
		mary = account("mary", "MANAGER");
		account("olivia", "OPERATOR");
		account("victor", "VIEWER");
	}

	@Test
	void anItemIsAddedWithAGeneratedSkuItsStartingStockAsARestockAnEventAndALogRow() throws Exception {
		createItem("mary", "{\"name\":\"  A4 paper \",\"startingQuantity\":25}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("A4 paper"))
				.andExpect(jsonPath("$.sku").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]{8}")))
				.andExpect(jsonPath("$.availableQuantity").value(25))
				.andExpect(jsonPath("$.listed").value(false))
				.andExpect(jsonPath("$.imageUrl").doesNotExist());
		long id = root().queryForObject("SELECT id FROM inventory WHERE name = 'A4 paper'", Long.class);

		assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id = ? AND type = 'RESTOCK' AND quantity = 25 AND available_before = 0 AND available_after = 25 AND reason = 'initial stock' AND operator_id = ?", id, mary)).isEqualTo(1);
		assertThat(itemEvents(id)).containsExactly("CREATED");
		assertThat(logActions("ITEM", id)).containsExactly("ITEM_CREATED");
		assertThat(logData("ITEM_CREATED", id, "after_data")).contains("\"quantity\": 25").contains("\"name\": \"A4 paper\"");
	}

	@Test
	void aStartingQuantityOfZeroOrNoneWritesNoTransaction() throws Exception {
		long none = item("mary", "Stapler", 0);
		createItem("mary", "{\"name\":\"Ruler\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.availableQuantity").value(0));
		assertThat(count("SELECT COUNT(*) FROM inventory_transactions")).isZero();
		assertThat(itemEvents(none)).containsExactly("CREATED");
	}

	@Test
	void aStartingQuantityThatIsNotAWholeNumberInItsRangeIsRefusedAndNothingIsWritten() throws Exception {
		for (String value : new String[] { "1.5", "\"ten\"", "-1", "1000000", "true" }) {
			createItem("mary", "{\"name\":\"Bad " + value.hashCode() + "\",\"startingQuantity\":" + value + "}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
		}
		createItem("mary", "{\"name\":\"Most\",\"startingQuantity\":999999}").andExpect(status().isCreated());
		createItem("mary", "{\"name\":\"Whole\",\"startingQuantity\":3.0}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.availableQuantity").value(3));
		assertThat(count("SELECT COUNT(*) FROM inventory")).isEqualTo(2);
	}

	@Test
	void aNameIsRequiredAndAtMost200Characters() throws Exception {
		createItem("mary", "{\"name\":\"   \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
		createItem("mary", "{\"name\":\"" + "x".repeat(201) + "\"}").andExpect(status().isBadRequest());
		createItem("mary", "{\"name\":\"" + "x".repeat(200) + "\"}").andExpect(status().isCreated());
	}

	@Test
	void aNameInUseIsRefusedWithoutRegardToCaseAndTheNameOfADeletedItemIsFreeAgain() throws Exception {
		long first = item("mary", "A4 paper", 1);
		createItem("mary", "{\"name\":\"a4 PAPER\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_NAME_TAKEN"));

		deleteItem("mary", first).andExpect(status().isNoContent());
		long again = item("mary", "a4 PAPER", 0);

		assertThat(again).isNotEqualTo(first);
		assertThat(itemRow(again).get("sku")).isNotEqualTo(itemRow(first).get("sku"));
	}

	@Test
	void theStockPageListsTheItemsThatAreNotDeletedByNameWithASearchAndPaging() throws Exception {
		long b = item("mary", "Binder", 5);
		item("mary", "Apple box", 2);
		long gone = item("mary", "Box cutter", 1);
		item("mary", "Cable", 0);
		deleteItem("mary", gone);
		listed("mary", b, "Binder", "3.00");

		getAs("victor", "/items").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].name", contains("Apple box", "Binder", "Cable")))
				.andExpect(jsonPath("$.items[1].listed").value(true))
				.andExpect(jsonPath("$.items[0].listed").value(false));
		getAs("victor", "/items?search=b").andExpect(jsonPath("$.items[*].name", contains("Apple box", "Binder", "Cable")));
		getAs("victor", "/items?search=BIN").andExpect(jsonPath("$.items[*].name", contains("Binder")));

		String firstPage = getAs("victor", "/items?limit=2").andExpect(jsonPath("$.items[*].name", contains("Apple box", "Binder")))
				.andReturn().getResponse().getContentAsString();
		String next = com.jayway.jsonpath.JsonPath.read(firstPage, "$.nextCursor");
		getAs("victor", "/items?limit=2&cursor=" + next).andExpect(jsonPath("$.items[*].name", contains("Cable")))
				.andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	@Test
	void anItemThatIsNotOnSaleCanBeRenamedAndGetAnImageAndTheChangeIsLogged() throws Exception {
		long id = item("mary", "Pen", 4);
		String path = com.jayway.jsonpath.JsonPath.read(upload("mary", PNG, "pen.png", "image/png").andReturn().getResponse()
				.getContentAsString(), "$.path");

		editItem("mary", id, "{\"name\":\"Blue pen\",\"imagePath\":\"" + path + "\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Blue pen"))
				.andExpect(jsonPath("$.imageUrl").value("/api/v1/images/" + path));

		assertThat(itemRow(id).get("name")).isEqualTo("Blue pen");
		assertThat(itemRow(id).get("image_url")).isEqualTo(path);
		assertThat(logActions("ITEM", id)).containsExactly("ITEM_CREATED", "ITEM_UPDATED");
		assertThat(logData("ITEM_UPDATED", id, "before_data")).contains("\"name\": \"Pen\"");

		editItem("mary", id, "{\"name\":\"Blue pen\",\"imagePath\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").doesNotExist());
		assertThat(itemRow(id).get("image_url")).isNull();
	}

	@Test
	void anEditThatChangesNothingWritesNoLogRow() throws Exception {
		long id = item("mary", "Pen", 4);
		editItem("mary", id, "{\"name\":\"Pen\"}").andExpect(status().isOk());
		assertThat(logActions("ITEM", id)).containsExactly("ITEM_CREATED");
	}

	@Test
	void anItemOnSaleCannotBeEditedADeletedOneIsNotFoundAndATakenNameIsRefused() throws Exception {
		long pen = item("mary", "Pen", 4);
		long ink = item("mary", "Ink", 4);
		long product = listed("mary", pen, "Pen", "1.00");

		editItem("mary", pen, "{\"name\":\"Red pen\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_LISTED"));
		delist("mary", product).andExpect(status().isOk());
		editItem("mary", pen, "{\"name\":\"INK\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_NAME_TAKEN"));

		deleteItem("mary", ink);
		editItem("mary", ink, "{\"name\":\"Black ink\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		editItem("mary", 999999, "{\"name\":\"Nothing\"}").andExpect(status().isNotFound());
	}

	@Test
	void anImagePathThatTheUploadDidNotReturnIsRefused() throws Exception {
		for (String path : new String[] { "items/../secret.png", "items/not-a-uuid.png", "items/3f2b8c1e-0000-0000-0000-000000000000.png" }) {
			createItem("mary", "{\"name\":\"Pic\",\"imagePath\":\"" + path + "\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("imagePath"));
		}
	}

	@Test
	void deletingAnItemDelistsItsProductRecordsTheStockItShowedLeavesTheStockAndLogsBoth() throws Exception {
		long id = item("mary", "Lamp", 7);
		long product = listed("mary", id, "Desk lamp", "20.00");

		deleteItem("mary", id).andExpect(status().isNoContent());

		assertThat(itemRow(id).get("deleted_at")).isNotNull();
		assertThat(((Number) itemRow(id).get("deleted_by")).longValue()).isEqualTo(mary);
		assertThat(itemRow(id).get("available_quantity")).isEqualTo(7);
		assertThat(productRow(product).get("status")).isEqualTo("UNLISTED");
		assertThat(listingEvents(product)).containsExactly("LISTED", "DELISTED");
		assertThat(itemEvents(id)).containsExactly("CREATED", "DELETED");
		assertThat(count("SELECT quantity FROM inventory_item_events WHERE inventory_id = ? AND action = 'DELETED'", id)).isEqualTo(7);
		assertThat(logActions("ITEM", id)).containsExactly("ITEM_CREATED", "ITEM_DELETED");
		assertThat(logActions("PRODUCT", product)).containsExactly("PRODUCT_LISTED", "PRODUCT_DELISTED");
		assertThat(root().queryForObject("SELECT remark FROM operation_logs WHERE action = 'PRODUCT_DELISTED' AND target_id = ?", String.class, product))
				.isEqualTo("ITEM_DELETED:" + id);
		assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id = ? AND type <> 'RESTOCK'", id)).isZero();

		deleteItem("mary", id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		getAs("victor", "/items").andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void anOperatorAndAViewerReadTheItemsButCannotChangeThem() throws Exception {
		long id = item("mary", "Glue", 1);
		getAs("olivia", "/items").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
		createItem("olivia", "{\"name\":\"Tape\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		editItem("victor", id, "{\"name\":\"Paste\"}").andExpect(status().isForbidden());
		deleteItem("olivia", id).andExpect(status().isForbidden());
		assertThat(itemRow(id).get("name")).isEqualTo("Glue");
	}

}
