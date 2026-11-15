package com.opsflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.validation.Quantities;
import com.opsflow.order.OrderRepository.PlacedOrder;

/**
 * Placing an order through HTTP (T-10, T-12, T-41): the order, its items, the deduction, the {@code SALE},
 * and the record of the attempt; a repeated key; each refusal, recorded with its number; and what is not
 * an attempt at all.
 */
class OrderTest extends OrderIntegrationSupport {

	private static final String NUMBER = "ORD-[0-9a-f]{32}";

	@Autowired
	OrderAttemptRecorder recorder;

	@Autowired
	OrderRepository repository;

	@Autowired
	OrderNumbers numbers;

	@Autowired
	Quantities quantities;

	@Autowired
	TransactionRetry retry;

	@Autowired
	OpsflowProperties properties;

	long olive;
	long paper;
	long paperProduct;

	@BeforeEach
	void shop() throws Exception {
		account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
		account("vera", "VIEWER");
		paper = item("mary", "A4 paper", 10);
		paperProduct = product("mary", paper, "A4 paper", "12.50");
	}

	@Test
	void anOrderTakesTheStockAndRecordsTheSaleAndTheAttempt() throws Exception {
		String answer = order("olive", null, line(paperProduct, "3", "A4 paper", "12.50")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderNo").value(org.hamcrest.Matchers.matchesPattern(NUMBER)))
				.andExpect(jsonPath("$.totalAmount").value("37.50")).andExpect(jsonPath("$.itemCount").value(1))
				.andExpect(jsonPath("$.lines[0].name").value("A4 paper")).andExpect(jsonPath("$.lines[0].unitPrice").value("12.50"))
				.andExpect(jsonPath("$.lines[0].quantity").value(3)).andExpect(jsonPath("$.lines[0].subtotal").value("37.50"))
				.andReturn().getResponse().getContentAsString();
		long orderId = ((Number) JsonPath.read(answer, "$.id")).longValue();
		String orderNo = JsonPath.read(answer, "$.orderNo");
		String sku = root().queryForObject("SELECT sku FROM inventory WHERE id = ?", String.class, paper);
		assertThat((String) JsonPath.read(answer, "$.lines[0].sku")).isEqualTo(sku);

		assertThat(stock(paper)).containsEntry("available_quantity", 7).containsEntry("sold_quantity", 3);
		Map<String, Object> sale = movements(paper).get(1);
		assertThat(sale).containsEntry("type", "SALE").containsEntry("quantity", 3).containsEntry("available_before", 10)
				.containsEntry("available_after", 7).containsEntry("order_id", orderId).containsEntry("operator_id", olive);
		assertThat(sale.get("reason")).isNull();
		assertThat(attempts(paperProduct)).singleElement().satisfies(attempt -> assertThat(attempt).containsEntry("order_no", orderNo)
				.containsEntry("order_id", orderId).containsEntry("result", "COMPLETED").containsEntry("quantity", 3)
				.containsEntry("stock_deducted", 3).containsEntry("product_name", "A4 paper"));
		assertThat(count("SELECT COUNT(*) FROM operation_logs WHERE target_type = 'ORDER'")).as("an order writes no audit row").isZero();
	}

	@Test
	void aRepeatedKeyReturnsTheOriginalOrderAndWritesNothing() throws Exception {
		String first = order("olive", "key-1", line(paperProduct, "2", "A4 paper", "12.50")).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		String again = order("olive", "key-1", line(paperProduct, "2", "A4 paper", "12.50")).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();

		assertThat((Object) JsonPath.read(again, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
		assertThat((String) JsonPath.read(again, "$.orderNo")).isEqualTo(JsonPath.read(first, "$.orderNo"));
		assertThat((String) JsonPath.read(again, "$.lines[0].subtotal")).isEqualTo("25.00");
		assertThat(available(paper)).isEqualTo(8);
		assertThat(attempts(paperProduct)).hasSize(1);
		assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
	}

	@Test
	void aKeyIsTheUsersOwnAndARefusedAttemptDoesNotUseItUp() throws Exception {
		account("otto", "OPERATOR");
		order("olive", "shared", line(paperProduct, "20", "A4 paper", "12.50")).andExpect(status().isConflict());
		order("olive", "shared", line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isCreated());
		order("otto", "shared", line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isCreated());
		assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(2);
	}

	@Test
	void anOrderOfSeveralProductsTakesAllOrNothing() throws Exception {
		long pens = item("mary", "Pens", 2);
		long pensProduct = product("mary", pens, "Pens", "1.20");

		order("olive", null, line(pensProduct, "3", "Pens", "1.20"), line(paperProduct, "1", "A4 paper", "12.50"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
		assertThat(available(paper)).as("the line that had stock is not taken either").isEqualTo(10);
		assertThat(attempts(paperProduct)).singleElement().satisfies(a -> assertThat(a).containsEntry("result", "OUT_OF_STOCK"));
		assertThat(attempts(pensProduct)).singleElement().satisfies(a -> assertThat(a).containsEntry("result", "OUT_OF_STOCK"));

		order("olive", null, line(pensProduct, "2", "Pens", "1.20"), line(paperProduct, "1", "A4 paper", "12.50"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.itemCount").value(2)).andExpect(jsonPath("$.totalAmount").value("14.90"));
		assertThat(available(pens)).isZero();
		assertThat(available(paper)).isEqualTo(9);
	}

	@Test
	void aDelistedProductIsRefusedAndTheAttemptIsRecordedWithItsNumber() throws Exception {
		postAs("mary", "/products/" + paperProduct + "/delist", "").andExpect(status().isOk());
		String answer = order("olive", null, line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_DELISTED"))
				.andExpect(jsonPath("$.orderNo").value(org.hamcrest.Matchers.matchesPattern(NUMBER))).andReturn().getResponse()
				.getContentAsString();

		assertThat(attempts(paperProduct)).singleElement().satisfies(attempt -> assertThat(attempt)
				.containsEntry("order_no", JsonPath.read(answer, "$.orderNo")).containsEntry("result", "PRODUCT_DELISTED")
				.containsEntry("order_id", null).containsEntry("stock_deducted", 0).containsEntry("quantity", 1));
		assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
		assertThat(available(paper)).isEqualTo(10);
	}

	@Test
	void aNameOrPriceThatChangedIsRefused() throws Exception {
		order("olive", null, line(paperProduct, "1", "A4 paper", "12.00")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_CHANGED"));
		order("olive", null, line(paperProduct, "1", "A4 Paper", "12.50")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_CHANGED"));
		// The same price, written without its last zero.
		order("olive", null, line(paperProduct, "1", "A4 paper", "12.5")).andExpect(status().isCreated());
		assertThat(attempts(paperProduct)).extracting(a -> a.get("result")).containsExactly("PRODUCT_CHANGED", "PRODUCT_CHANGED", "COMPLETED");
	}

	@Test
	void notEnoughStockIsRefusedAndNothingIsTaken() throws Exception {
		order("olive", null, line(paperProduct, "11", "A4 paper", "12.50")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK")).andExpect(jsonPath("$.orderNo").exists());
		assertThat(stock(paper)).containsEntry("available_quantity", 10).containsEntry("sold_quantity", 0);
		assertThat(movements(paper)).hasSize(1);
	}

	@Test
	void aQuantityThatIsNotValidIsRefusedAndRecordedAsItWasSent() throws Exception {
		for (String quantity : new String[] { "0", "10000", "-4", "1.5", "\"ten\"", "null" }) {
			order("olive", null, line(paperProduct, quantity, "A4 paper", "12.50")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_QUANTITY")).andExpect(jsonPath("$.orderNo").exists());
		}
		assertThat(attempts(paperProduct)).extracting(a -> a.get("quantity")).containsExactly(0, 10000, -4, 0, 0, 0);
		assertThat(attempts(paperProduct)).allSatisfy(a -> assertThat(a).containsEntry("result", "INVALID_QUANTITY"));
		assertThat(available(paper)).isEqualTo(10);
	}

	@Test
	void aMalformedRequestIsNoAttemptAndWritesNothing() throws Exception {
		order("olive", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines"));
		order("olive", null, line(paperProduct, "1", "A4 paper", "12.50"), line(paperProduct, "1", "A4 paper", "12.50"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines[1].productId"));
		order("olive", null, "{\"productId\":" + paperProduct + ",\"quantity\":1,\"name\":\"A4 paper\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("lines[0].price"));
		order("olive", null, line(paperProduct, "1", "A4 paper", "12.505")).andExpect(status().isBadRequest());
		order("olive", "", line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
		assertThat(count("SELECT COUNT(*) FROM order_attempts")).isZero();
	}

	@Test
	void aProductThatDoesNotExistIsNotFoundAndCannotBeRecorded() throws Exception {
		order("olive", null, line(paperProduct + 100, "1", "Ghost", "1.00")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.orderNo").doesNotExist());
		assertThat(count("SELECT COUNT(*) FROM order_attempts")).isZero();
	}

	@Test
	void onlyARoleThatMayOrderCanOrder() throws Exception {
		order("vera", null, line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		order("mary", null, line(paperProduct, "1", "A4 paper", "12.50")).andExpect(status().isCreated());
		assertThat(count("SELECT COUNT(*) FROM order_attempts WHERE user_id = (SELECT id FROM users WHERE username = 'vera')")).isZero();
	}

	@Test
	void anAttemptThatLosesEveryRetryIsBusyAndRecorded() {
		OrderWriter alwaysLocked = new OrderWriter(repository, clock) {
			@Override
			public PlacedOrder place(long userId, String orderNo, String key, String remark, List<Line> lines) {
				throw new CannotAcquireLockException("Lock wait timeout exceeded");
			}
		};
		OrderService service = new OrderService(alwaysLocked, recorder, repository, numbers, quantities, retry, properties);

		ApiException busy = org.assertj.core.api.Assertions.catchThrowableOfType(ApiException.class,
				() -> service.place(actor(olive), null, List.of(new OrderService.LineRequest(paperProduct, 2, "A4 paper", "12.50")), null));

		assertThat(busy.code().name()).isEqualTo("BUSY");
		assertThat(busy.extras()).containsKey("orderNo");
		assertThat(attempts(paperProduct)).singleElement().satisfies(attempt -> assertThat(attempt).containsEntry("result", "BUSY")
				.containsEntry("order_no", busy.extras().get("orderNo")).containsEntry("quantity", 2));
	}

}
