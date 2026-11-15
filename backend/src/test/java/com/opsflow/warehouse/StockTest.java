package com.opsflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Manual stock changes through HTTP (T-30, T-41): a stock-in, a write-off, and a correction, each with its
 * movement, its exact snapshots, and its log row; and every refusal, which writes nothing.
 */
class StockTest extends WarehouseIntegrationSupport {

	long olive;
	long paper;

	@BeforeEach
	void stockRoom() throws Exception {
		account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
		account("vera", "VIEWER");
		paper = item("mary", "A4 paper", 10);
	}

	private ResultActions change(String username, long id, String action, String json) throws Exception {
		return mvc.perform(post("/api/v1/items/" + id + "/" + action).header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private List<Map<String, Object>> movements() {
		return root().queryForList("""
				SELECT id, type, quantity, available_before, available_after, operator_id, reason
				FROM inventory_transactions WHERE inventory_id = ? ORDER BY id
				""", paper);
	}

	@Test
	void aStockInAddsTheStockAndRecordsTheMovementAndTheLog() throws Exception {
		change("olive", paper, "stock-in", "{\"quantity\":5,\"reason\":\"  delivery 42  \"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(15)).andExpect(jsonPath("$.listed").value(false));

		Map<String, Object> movement = movements().get(1);
		assertThat(movement).containsEntry("type", "RESTOCK").containsEntry("quantity", 5).containsEntry("available_before", 10)
				.containsEntry("available_after", 15).containsEntry("operator_id", olive).containsEntry("reason", "delivery 42");
		assertThat(root().queryForObject("SELECT sold_quantity FROM inventory WHERE id = ?", Integer.class, paper)).isZero();
		assertThat(logActions("ITEM", paper)).containsExactly("ITEM_CREATED", "STOCK_RESTOCK");
		assertThat(logData("STOCK_RESTOCK", paper, "before_data")).isEqualTo("{\"availableQuantity\": 10}");
		assertThat(logData("STOCK_RESTOCK", paper, "after_data")).contains("\"availableQuantity\": 15", "\"quantity\": 5",
				"\"name\": \"A4 paper\"", "\"transactionId\": " + movement.get("id"));
		assertThat(logData("STOCK_RESTOCK", paper, "remark")).isEqualTo("delivery 42");
	}

	@Test
	void aWriteOffTakesStockButNeverMoreThanThereIs() throws Exception {
		change("olive", paper, "write-off", "{\"quantity\":11,\"reason\":\"damaged\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
		change("olive", paper, "write-off", "{\"quantity\":10,\"reason\":\"damaged\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(0));
		assertThat(movements()).extracting(m -> m.get("type")).containsExactly("RESTOCK", "WRITE_OFF");
		assertThat(logActions("ITEM", paper)).containsExactly("ITEM_CREATED", "STOCK_WRITE_OFF");
	}

	@Test
	void aCorrectionSetsTheCountedNumberInEitherDirection() throws Exception {
		change("olive", paper, "correction", "{\"quantity\":14,\"expectedQuantity\":10,\"reason\":\"count\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(14));
		change("olive", paper, "correction", "{\"quantity\":0,\"expectedQuantity\":14,\"reason\":\"count\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(0));

		assertThat(movements()).extracting(m -> m.get("type") + " " + m.get("quantity") + " " + m.get("available_after"))
				.containsExactly("RESTOCK 10 10", "ADJUST_IN 4 14", "ADJUST_OUT 14 0");
		assertThat(logActions("ITEM", paper)).containsExactly("ITEM_CREATED", "STOCK_ADJUSTED", "STOCK_ADJUSTED");
		assertThat(logData("STOCK_ADJUSTED", paper, "after_data")).contains("\"direction\": \"OUT\"");
	}

	@Test
	void aCorrectionFromAStaleScreenIsRefusedAndOneThatChangesNothingTooAndBothWriteNothing() throws Exception {
		change("olive", paper, "correction", "{\"quantity\":4,\"expectedQuantity\":9,\"reason\":\"count\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("STOCK_CHANGED"));
		change("olive", paper, "correction", "{\"quantity\":10,\"expectedQuantity\":10,\"reason\":\"count\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("quantity"));
		change("olive", paper, "correction", "{\"quantity\":4,\"reason\":\"count\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
		assertThat(movements()).hasSize(1);
		assertThat(logActions("ITEM", paper)).containsExactly("ITEM_CREATED");
	}

	@Test
	void everyManualChangeNeedsAReason() throws Exception {
		for (String reason : new String[] { "", "\"   \"", "null", "\"" + "x".repeat(256) + "\"" }) {
			String json = "{\"quantity\":1" + (reason.isEmpty() ? "" : ",\"reason\":" + reason) + "}";
			change("olive", paper, "stock-in", json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
		}
		change("olive", paper, "stock-in", "{\"quantity\":1,\"reason\":\"" + "x".repeat(255) + "\"}").andExpect(status().isOk());
	}

	@Test
	void aQuantityThatIsNotValidIsRefused() throws Exception {
		for (String quantity : new String[] { "0", "1000000", "1.5", "\"5\"", "null", "-1" }) {
			change("olive", paper, "stock-in", "{\"quantity\":" + quantity + ",\"reason\":\"r\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
			change("olive", paper, "write-off", "{\"quantity\":" + quantity + ",\"reason\":\"r\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
		}
		change("olive", paper, "correction", "{\"quantity\":1000000,\"expectedQuantity\":10,\"reason\":\"r\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
		assertThat(movements()).hasSize(1);
	}

	@Test
	void aStockInThatWouldPassWhatTheColumnHoldsIsRefused() throws Exception {
		root().update("UPDATE inventory SET available_quantity = ? WHERE id = ?", Integer.MAX_VALUE - 5, paper);
		change("olive", paper, "stock-in", "{\"quantity\":6,\"reason\":\"r\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
		change("olive", paper, "stock-in", "{\"quantity\":5,\"reason\":\"r\"}").andExpect(status().isOk());
	}

	@Test
	void aDeletedOrUnknownItemCannotBeChanged() throws Exception {
		deleteItem("mary", paper).andExpect(status().isNoContent());
		change("olive", paper, "stock-in", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isNotFound());
		change("olive", paper, "write-off", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isNotFound());
		change("olive", paper, "correction", "{\"quantity\":1,\"expectedQuantity\":10,\"reason\":\"r\"}").andExpect(status().isNotFound());
		change("olive", paper + 100, "stock-in", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isNotFound());
		assertThat(movements()).hasSize(1);
	}

	@Test
	void anOperatorAManagerAndAnAdministratorChangeStockAndAViewerDoesNot() throws Exception {
		account("adam", "ADMIN");
		change("vera", paper, "stock-in", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isForbidden());
		change("vera", paper, "write-off", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isForbidden());
		change("vera", paper, "correction", "{\"quantity\":1,\"expectedQuantity\":10,\"reason\":\"r\"}").andExpect(status().isForbidden());
		change("mary", paper, "stock-in", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isOk());
		change("adam", paper, "write-off", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isOk());
		long product = listed("mary", paper, "A4 paper", "1.00");
		change("olive", paper, "stock-in", "{\"quantity\":1,\"reason\":\"r\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.listed").value(true));
		assertThat(product).isPositive();
	}

}
