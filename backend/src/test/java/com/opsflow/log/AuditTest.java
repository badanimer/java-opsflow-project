package com.opsflow.log;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
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
 * The audit log page through HTTP (T-48): one area of {@code operation_logs} at a time with its action, time,
 * and user filters, System for the actions of the system, the Orders area from {@code order_attempts}, and who
 * may read it. The rows are written by the real changes of the other modules.
 */
@AutoConfigureMockMvc
class AuditTest extends MySqlIntegrationTest {

	private static final String PASSWORD = "correct-horse-1";
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

	/** 2026-11-16 at noon in Sydney. */
	private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

	@Autowired
	MockMvc mvc;

	private final Map<String, String> tokens = new HashMap<>();

	long mary;
	long olive;
	long paper;
	long pens;
	long ticket;
	String ticketNo;

	@BeforeEach
	void history() throws Exception {
		clock.set(NOW);
		account("adam", "ADMIN");
		mary = account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
		account("vera", "VIEWER");
		paper = id(post("mary", "/items", "{\"name\":\"A4 paper\",\"startingQuantity\":10}"));
		pens = id(post("mary", "/items", "{\"name\":\"Pens\",\"startingQuantity\":5}"));
		long paperProduct = id(post("mary", "/products", "{\"inventoryId\":" + paper + ",\"name\":\"A4 paper\",\"price\":\"12.50\"}"));
		post("olive", "/orders", "{\"lines\":[{\"productId\":" + paperProduct + ",\"quantity\":3,\"name\":\"A4 paper\",\"price\":\"12.50\"}]}")
				.andExpect(status().isCreated());
		post("olive", "/orders", "{\"lines\":[{\"productId\":" + paperProduct + ",\"quantity\":20,\"name\":\"A4 paper\",\"price\":\"12.50\"}]}")
				.andExpect(status().isConflict());
		post("olive", "/items/" + pens + "/stock-in", "{\"quantity\":4,\"reason\":\"delivery\"}").andExpect(status().isOk());
		ticket = id(post("olive", "/tickets", "{\"type\":\"INVENTORY_EXCEPTION\",\"title\":\"Pens short\",\"description\":\"Count is off\",\"inventoryIds\":[" + pens + "]}"));
		ticketNo = root().queryForObject("SELECT ticket_no FROM tickets WHERE id = ?", String.class, ticket);
		mvc.perform(delete("/api/v1/items/" + pens).header("Authorization", "Bearer " + token("mary"))).andExpect(status().isNoContent());
	}

	@Test
	void anAreaListsItsActionsNewestFirstWithWhoDidItAndTheDataOfTheRow() throws Exception {
		read("mary", "/audit-records?area=ITEM").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].action").value(contains("ITEM_DELETED", "ITEM_CREATED", "ITEM_CREATED")))
				.andExpect(jsonPath("$.items[0].by").value("mary")).andExpect(jsonPath("$.items[0].userId").value(mary))
				.andExpect(jsonPath("$.items[0].area").value("ITEM")).andExpect(jsonPath("$.items[0].targetType").value("ITEM"))
				.andExpect(jsonPath("$.items[0].targetId").value(pens)).andExpect(jsonPath("$.items[0].after.name").value("Pens"))
				.andExpect(jsonPath("$.items[0].after.cancelledTicketIds[0]").value(ticket))
				.andExpect(jsonPath("$.items[0].after.cancelledTicketNos[0]").value(ticketNo))
				.andExpect(jsonPath("$.items[0].before.availableQuantity").value(9)).andExpect(jsonPath("$.items[0].at").value("2026-11-16T01:00:00Z"));
		read("mary", "/audit-records?area=item&order=asc").andExpect(jsonPath("$.items[0].action").value("ITEM_CREATED"))
				.andExpect(jsonPath("$.items[0].before").isEmpty());
		read("mary", "/audit-records?area=STOCK").andExpect(jsonPath("$.items[*].action").value(contains("STOCK_RESTOCK")))
				.andExpect(jsonPath("$.items[0].remark").value("delivery")).andExpect(jsonPath("$.items[0].by").value("olive"));
	}

	@Test
	void theActionTheUserAndSystemNarrowTheArea() throws Exception {
		read("mary", "/audit-records?area=ITEM&action=item_created").andExpect(jsonPath("$.items.length()").value(2));
		read("mary", "/audit-records?area=ITEM&action=STOCK_RESTOCK").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("action"));
		read("mary", "/audit-records?area=TICKET&userId=" + olive).andExpect(jsonPath("$.items[*].action").value(contains("TICKET_CREATED")));
		read("mary", "/audit-records?area=TICKET&system=true").andExpect(jsonPath("$.items[*].action").value(contains("TICKET_CANCELLED")))
				.andExpect(jsonPath("$.items[0].by").isEmpty()).andExpect(jsonPath("$.items[0].userId").isEmpty())
				.andExpect(jsonPath("$.items[0].remark").value("ITEM_DELETED:" + pens));
		read("mary", "/audit-records?area=TICKET&system=true&userId=" + olive).andExpect(status().isBadRequest());
		read("mary", "/audit-records?area=PRODUCT&userId=" + olive).andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void theAreaIsRequiredAndTheTimeFilterTakesSydneyDays() throws Exception {
		read("mary", "/audit-records").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("area"));
		read("mary", "/audit-records?area=ORDER").andExpect(status().isBadRequest());
		read("mary", "/audit-records?area=ITEM&from=2026-11-16").andExpect(jsonPath("$.items.length()").value(3));
		read("mary", "/audit-records?area=ITEM&from=2026-11-15&to=2026-11-15").andExpect(jsonPath("$.items.length()").value(0));
		read("mary", "/audit-records?area=ITEM&from=2026-11-17").andExpect(status().isBadRequest());
	}

	@Test
	void theOrdersAreaReadsTheAttemptsWithTheResultAndTheUser() throws Exception {
		read("adam", "/audit-order-records").andExpect(jsonPath("$.items[*].result").value(contains("OUT_OF_STOCK", "COMPLETED")))
				.andExpect(jsonPath("$.items[0].orderedBy").value("olive"));
		read("adam", "/audit-order-records?result=completed").andExpect(jsonPath("$.items.length()").value(1));
		read("adam", "/audit-order-records?userId=" + mary).andExpect(jsonPath("$.items.length()").value(0));
		read("adam", "/audit-order-records?result=LOST").andExpect(status().isBadRequest());
	}

	@Test
	void onlyAnAdministratorAndAManagerReadTheAuditPage() throws Exception {
		read("adam", "/audit-records?area=ACCOUNT").andExpect(status().isOk());
		read("vera", "/audit-records?area=ITEM").andExpect(status().isForbidden());
		read("olive", "/audit-records?area=ITEM").andExpect(status().isForbidden());
		read("vera", "/audit-order-records").andExpect(status().isForbidden());
		read("mary", "/user-options?use=filter").andExpect(status().isOk());
	}

	// Helpers

	private long account(String username, String... roleCodes) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, PASSWORD_HASH,
				username + "@example.com");
		long id = root().queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
		for (String code : roleCodes) {
			root().update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, code);
		}
		return id;
	}

	private String token(String username) throws Exception {
		String known = tokens.get(username);
		if (known != null) {
			return known;
		}
		String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
		String answer = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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

}
