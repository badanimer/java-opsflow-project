package com.opsflow.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * What the tests of orders and stock changes share: accounts with roles and their tokens, items and
 * products made through the warehouse endpoints, the calls of the order and stock endpoints, and reads of
 * the rows they write.
 */
@AutoConfigureMockMvc
abstract class OrderIntegrationSupport extends MySqlIntegrationTest {

	static final String PASSWORD = "correct-horse-1";
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

	@Autowired
	MockMvc mvc;

	/** A token per username: a second login would end the first (one signed-in device). */
	private final Map<String, String> tokens = new HashMap<>();

	long account(String username, String... roleCodes) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, PASSWORD_HASH,
				username + "@example.com");
		long id = root().queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
		for (String code : roleCodes) {
			root().update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, code);
		}
		return id;
	}

	String token(String username) throws Exception {
		String known = tokens.get(username);
		if (known != null) {
			return known;
		}
		String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
		String answer = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(answer, "$.token");
		tokens.put(username, token);
		return token;
	}

	static CurrentUser actor(long id) {
		return new CurrentUser(id, "actor-" + id, List.of());
	}

	ResultActions postAs(String username, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Adds an item through the endpoint and returns its id. */
	long item(String manager, String name, int startingQuantity) throws Exception {
		String answer = postAs(manager, "/items", "{\"name\":\"" + name + "\",\"startingQuantity\":" + startingQuantity + "}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	/** Lists an item through the endpoint and returns the product id. */
	long product(String manager, long inventoryId, String name, String price) throws Exception {
		String json = "{\"inventoryId\":" + inventoryId + ",\"name\":\"" + name + "\",\"price\":\"" + price + "\"}";
		String answer = postAs(manager, "/products", json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	/** One line of an order. {@code quantity} is written into the JSON as it is, so it may be any JSON value. */
	static String line(long productId, String quantity, String name, String price) {
		return "{\"productId\":" + productId + ",\"quantity\":" + quantity + ",\"name\":\"" + name + "\",\"price\":\"" + price + "\"}";
	}

	ResultActions order(String username, String key, String... lines) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/orders").header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[" + String.join(",", lines) + "]}");
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		return mvc.perform(request);
	}

	// The rows

	Map<String, Object> stock(long inventoryId) {
		return root().queryForMap("SELECT available_quantity, sold_quantity FROM inventory WHERE id = ?", inventoryId);
	}

	int available(long inventoryId) {
		return root().queryForObject("SELECT available_quantity FROM inventory WHERE id = ?", Integer.class, inventoryId);
	}

	List<Map<String, Object>> attempts(long productId) {
		return root().queryForList("""
				SELECT order_no, user_id, product_name, order_id, quantity, result, stock_deducted
				FROM order_attempts WHERE product_id = ? ORDER BY id
				""", productId);
	}

	List<Map<String, Object>> movements(long inventoryId) {
		return root().queryForList("""
				SELECT type, quantity, available_before, available_after, order_id, operator_id, reason
				FROM inventory_transactions WHERE inventory_id = ? ORDER BY id
				""", inventoryId);
	}

	int count(String sql, Object... args) {
		return root().queryForObject(sql, Integer.class, args);
	}

}
