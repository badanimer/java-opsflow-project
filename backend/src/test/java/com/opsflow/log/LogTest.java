package com.opsflow.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * The two logs, the Deleted items module, and the product picker through HTTP (T-46, T-50): what each kind
 * of record shows, a product or item chosen, the order number used alone, the time filter in Sydney days,
 * both directions, the paging by key, and who may read them.
 */
@AutoConfigureMockMvc
class LogTest extends MySqlIntegrationTest {

	private static final String PASSWORD = "correct-horse-1";
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

	/** 2026-11-16 at noon in Sydney. */
	private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

	@Autowired
	MockMvc mvc;

	private final Map<String, String> tokens = new HashMap<>();

	long paper;
	long pens;
	long paperProduct;
	long pensProduct;
	String placedNo;

	@BeforeEach
	void history() throws Exception {
		clock.set(NOW);
		account("mary", "MANAGER");
		account("olive", "OPERATOR");
		account("vera", "VIEWER");
		paper = id(post("mary", "/items", "{\"name\":\"A4 paper\",\"startingQuantity\":10}"));
		pens = id(post("mary", "/items", "{\"name\":\"Pens\",\"startingQuantity\":5}"));
		paperProduct = id(post("mary", "/products", "{\"inventoryId\":" + paper + ",\"name\":\"A4 paper\",\"price\":\"12.50\"}"));
		pensProduct = id(post("mary", "/products", "{\"inventoryId\":" + pens + ",\"name\":\"Pens\",\"price\":\"1.20\"}"));
		String placed = post("olive", "/orders", order(paperProduct, 3, "A4 paper", "12.50")).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		placedNo = JsonPath.read(placed, "$.orderNo");
		post("olive", "/orders", order(paperProduct, 20, "A4 paper", "12.50")).andExpect(status().isConflict());
		post("olive", "/items/" + pens + "/stock-in", "{\"quantity\":4,\"reason\":\"delivery\"}").andExpect(status().isOk());
		post("mary", "/products/" + pensProduct + "/delist", "").andExpect(status().isOk());
		mvc.perform(delete("/api/v1/items/" + pens).header("Authorization", "Bearer " + token("mary"))).andExpect(status().isNoContent());
	}

	@Test
	void theListingRecordsShowEachListingAndDelistingNewestFirstOrOldestFirst() throws Exception {
		read("vera", "/listing-records").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].action").value(org.hamcrest.Matchers.contains("DELISTED", "LISTED", "LISTED")))
				.andExpect(jsonPath("$.items[0].name").value("Pens")).andExpect(jsonPath("$.items[0].price").value("1.20"))
				.andExpect(jsonPath("$.items[0].by").value("mary")).andExpect(jsonPath("$.items[0].productId").value(pensProduct));
		read("vera", "/listing-records?order=asc").andExpect(jsonPath("$.items[*].action")
				.value(org.hamcrest.Matchers.contains("LISTED", "LISTED", "DELISTED")));
		read("vera", "/listing-records?productId=" + paperProduct).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].name").value("A4 paper"));
	}

	@Test
	void theOrderRecordsShowEveryAttemptAndAreFoundByOneOrderNumberAlone() throws Exception {
		read("vera", "/order-records?productId=" + paperProduct).andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].result").value("OUT_OF_STOCK")).andExpect(jsonPath("$.items[0].stockDeducted").value(0))
				.andExpect(jsonPath("$.items[0].quantity").value(20)).andExpect(jsonPath("$.items[1].result").value("COMPLETED"))
				.andExpect(jsonPath("$.items[1].orderNo").value(placedNo)).andExpect(jsonPath("$.items[1].orderedBy").value("olive"))
				.andExpect(jsonPath("$.items[1].productName").value("A4 paper"));
		read("vera", "/order-records?orderNo=" + placedNo.toUpperCase()).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].stockDeducted").value(3));
		read("vera", "/order-records?orderNo=ORD-nothing").andExpect(jsonPath("$.items.length()").value(0));
		read("vera", "/order-records?orderNo=" + placedNo + "&productId=" + paperProduct).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("orderNo"));
		read("vera", "/order-records?orderNo=" + placedNo + "&from=2026-11-16").andExpect(status().isBadRequest());
	}

	@Test
	void theItemRecordsShowAdditionsAndDeletionsWithTheStockAtThatMoment() throws Exception {
		read("vera", "/item-records").andExpect(jsonPath("$.items[*].action").value(org.hamcrest.Matchers.contains("DELETED", "CREATED", "CREATED")))
				.andExpect(jsonPath("$.items[0].quantity").value(9)).andExpect(jsonPath("$.items[0].name").value("Pens"))
				.andExpect(jsonPath("$.items[2].quantity").value(10));
		read("vera", "/item-records?inventoryId=" + paper).andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void theStockMovementsShowASignedAmountTheReasonAndTheOrderNumberOfASale() throws Exception {
		read("vera", "/stock-movements?inventoryId=" + paper).andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].type").value("SALE")).andExpect(jsonPath("$.items[0].amount").value(-3))
				.andExpect(jsonPath("$.items[0].orderNo").value(placedNo)).andExpect(jsonPath("$.items[0].by").value("olive"))
				.andExpect(jsonPath("$.items[1].type").value("RESTOCK")).andExpect(jsonPath("$.items[1].amount").value(10))
				.andExpect(jsonPath("$.items[1].reason").value("initial stock")).andExpect(jsonPath("$.items[1].orderNo").isEmpty());
		read("vera", "/stock-movements?inventoryId=" + pens)
				.andExpect(jsonPath("$.items[0].reason").value("delivery")).andExpect(jsonPath("$.items[0].amount").value(4));
		read("vera", "/stock-movements?orderNo=" + placedNo).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].itemName").value("A4 paper"));
	}

	@Test
	void theTimeFilterTakesDaysInSydneyTime() throws Exception {
		// 13:30 on 1 November in Sydney is 02:30 UTC; 23:30 on 31 October in Sydney is 12:30 UTC on 31 October.
		root().update("UPDATE inventory_item_events SET created_at = '2026-11-01 02:30:00' WHERE inventory_id = ? AND action = 'CREATED'", paper);
		root().update("UPDATE inventory_item_events SET created_at = '2026-10-31 12:30:00' WHERE inventory_id = ? AND action = 'CREATED'", pens);

		read("vera", "/item-records?from=2026-11-01&to=2026-11-01").andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].inventoryId").value(paper));
		read("vera", "/item-records?from=2026-10-31&to=2026-10-31").andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].inventoryId").value(pens));
		read("vera", "/item-records?from=2026-11-01").andExpect(jsonPath("$.items.length()").value(2));
		read("vera", "/item-records?from=2026-11-17").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("from"));
		read("vera", "/item-records?from=2026-11-05&to=2026-11-04").andExpect(status().isBadRequest());
		read("vera", "/item-records?to=2026-11-04").andExpect(status().isBadRequest());
	}

	@Test
	void aLogIsPagedByKeyInBothDirections() throws Exception {
		for (int i = 0; i < 11; i++) {
			post("olive", "/items/" + paper + "/stock-in", "{\"quantity\":1,\"reason\":\"r" + i + "\"}").andExpect(status().isOk());
		}
		String first = read("vera", "/stock-movements?inventoryId=" + paper).andExpect(jsonPath("$.items.length()").value(10))
				.andExpect(jsonPath("$.prevCursor").isEmpty()).andReturn().getResponse().getContentAsString();
		String next = JsonPath.read(first, "$.nextCursor");
		String second = read("vera", "/stock-movements?inventoryId=" + paper + "&cursor=" + next).andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.nextCursor").isEmpty()).andExpect(jsonPath("$.items[2].type").value("RESTOCK")).andReturn().getResponse()
				.getContentAsString();
		String back = read("vera", "/stock-movements?inventoryId=" + paper + "&direction=prev&cursor=" + JsonPath.read(second, "$.prevCursor"))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Object>>read(back, "$.items[*].id")).isEqualTo(JsonPath.read(first, "$.items[*].id"));
		read("vera", "/stock-movements?cursor=bm90LWEtdGltZQ").andExpect(status().isBadRequest());
	}

	@Test
	void theDeletedItemsAreListedNewestFirstAndFoundByNameOrSku() throws Exception {
		String sku = root().queryForObject("SELECT sku FROM inventory WHERE id = ?", String.class, pens);
		read("vera", "/deleted-items").andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("Pens"))
				.andExpect(jsonPath("$.items[0].deletedBy").value("mary")).andExpect(jsonPath("$.items[0].productId").value(pensProduct))
				.andExpect(jsonPath("$.items[0].sku").value(sku));
		read("vera", "/deleted-items?search=pen").andExpect(jsonPath("$.items.length()").value(1));
		read("vera", "/deleted-items?search=" + sku.substring(2, 6)).andExpect(jsonPath("$.items.length()").value(1));
		read("vera", "/deleted-items?search=paper").andExpect(jsonPath("$.items.length()").value(0));
		read("vera", "/deleted-items?order=asc").andExpect(status().isBadRequest());
	}

	@Test
	void theProductPickerOffersEveryProductEverListedExceptThoseOfDeletedItems() throws Exception {
		long tape = id(post("mary", "/items", "{\"name\":\"Tape\",\"startingQuantity\":1}"));
		long tapeProduct = id(post("mary", "/products", "{\"inventoryId\":" + tape + ",\"name\":\"Tape\",\"price\":\"2.00\"}"));
		post("mary", "/products/" + tapeProduct + "/delist", "").andExpect(status().isOk());

		read("vera", "/product-options").andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("A4 paper", "Tape")))
				.andExpect(jsonPath("$.items[0].sku").isNotEmpty());
		read("vera", "/product-options?search=TAP").andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains((int) tapeProduct)));
	}

	@Test
	void everyRoleReadsTheLogsAndARequestWithoutATokenIsRefused() throws Exception {
		for (String path : new String[] { "/listing-records", "/order-records", "/item-records", "/stock-movements", "/deleted-items",
				"/product-options" }) {
			read("olive", path).andExpect(status().isOk());
			read("mary", path).andExpect(status().isOk());
			mvc.perform(get("/api/v1" + path)).andExpect(status().isUnauthorized());
		}
	}

	// Helpers

	private void account(String username, String... roleCodes) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, PASSWORD_HASH, username + "@example.com");
		for (String code : roleCodes) {
			root().update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.code = ?",
					username, code);
		}
	}

	private String token(String username) throws Exception {
		String known = tokens.get(username);
		if (known != null) {
			return known;
		}
		String answer = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		String token = JsonPath.read(answer, "$.token");
		tokens.put(username, token);
		return token;
	}

	private ResultActions post(String username, String path, String json) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1" + path)
				.header("Authorization", "Bearer " + token(username)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions read(String username, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token(username)));
	}

	private static long id(ResultActions created) throws Exception {
		String answer = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	private static String order(long productId, int quantity, String name, String price) {
		return "{\"lines\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + ",\"name\":\"" + name + "\",\"price\":\"" + price + "\"}]}";
	}

}
