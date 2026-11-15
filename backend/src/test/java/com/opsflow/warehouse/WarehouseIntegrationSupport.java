package com.opsflow.warehouse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * What the tests of the warehouse share: accounts with roles, their tokens, the HTTP calls of the
 * endpoints, image bytes of each kind, and reads of the rows the changes write.
 */
@AutoConfigureMockMvc
abstract class WarehouseIntegrationSupport extends MySqlIntegrationTest {

	static final String PASSWORD = "correct-horse-1";
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

	/** The first bytes of each accepted type, followed by some data. */
	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
	static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };

	@Autowired
	MockMvc mvc;

	/** A token per username: a second login would end the first (one signed-in device). */
	private final Map<String, String> tokens = new HashMap<>();

	/** An active account with these roles. */
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

	// The endpoints

	ResultActions getAs(String username, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token(username)));
	}

	ResultActions createItem(String username, String json) throws Exception {
		return mvc.perform(post("/api/v1/items").header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions editItem(String username, long id, String json) throws Exception {
		return mvc.perform(put("/api/v1/items/" + id).header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions deleteItem(String username, long id) throws Exception {
		return mvc.perform(delete("/api/v1/items/" + id).header("Authorization", "Bearer " + token(username)));
	}

	ResultActions listItem(String username, long inventoryId, String name, String price) throws Exception {
		String json = "{\"inventoryId\":" + inventoryId + ",\"name\":\"" + name + "\",\"price\":\"" + price + "\"}";
		return mvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions delist(String username, long productId) throws Exception {
		return mvc.perform(post("/api/v1/products/" + productId + "/delist").header("Authorization", "Bearer " + token(username)));
	}

	ResultActions upload(String username, byte[] content, String fileName, String contentType) throws Exception {
		return mvc.perform(multipart("/api/v1/item-images").file(new MockMultipartFile("file", fileName, contentType, content))
				.header("Authorization", "Bearer " + token(username)));
	}

	/** Adds an item through the endpoint and returns its id. */
	long item(String username, String name, int startingQuantity) throws Exception {
		String answer = createItem(username, "{\"name\":\"" + name + "\",\"startingQuantity\":" + startingQuantity + "}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	/** Lists an item through the endpoint and returns the product id. */
	long listed(String username, long inventoryId, String name, String price) throws Exception {
		String answer = listItem(username, inventoryId, name, price).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	// The rows

	Map<String, Object> itemRow(long id) {
		return root().queryForMap("SELECT sku, name, image_url, available_quantity, deleted_at, deleted_by FROM inventory WHERE id = ?", id);
	}

	Map<String, Object> productRow(long id) {
		return root().queryForMap("SELECT inventory_id, name, price, status, delisted_at FROM products WHERE id = ?", id);
	}

	List<String> itemEvents(long inventoryId) {
		return root().queryForList("SELECT action FROM inventory_item_events WHERE inventory_id = ? ORDER BY id", String.class, inventoryId);
	}

	List<String> listingEvents(long productId) {
		return root().queryForList("SELECT action FROM product_listing_events WHERE product_id = ? ORDER BY id", String.class, productId);
	}

	List<String> logActions(String targetType, long targetId) {
		return root().queryForList("SELECT action FROM operation_logs WHERE target_type = ? AND target_id = ? ORDER BY id", String.class,
				targetType, targetId);
	}

	String logData(String action, long targetId, String column) {
		return root().queryForObject("SELECT CAST(" + column + " AS CHAR) FROM operation_logs WHERE action = ? AND target_id = ? ORDER BY id DESC LIMIT 1",
				String.class, action, targetId);
	}

	int count(String sql, Object... args) {
		return root().queryForObject(sql, Integer.class, args);
	}

}
