package com.opsflow.ticket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * What the tests of the ticket module share: accounts with roles, their tokens, the HTTP calls of the
 * ticket endpoints, rows made directly for a starting state, and reads of the rows the changes write.
 */
@AutoConfigureMockMvc
abstract class TicketIntegrationSupport extends MySqlIntegrationTest {

	static final String PASSWORD = "correct-horse-1";
	private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
	private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 0, 0);

	@Autowired
	MockMvc mvc;

	@Autowired
	TicketService tickets;

	private final Map<String, String> tokens = new HashMap<>();
	private final Map<Long, CurrentUser> users = new HashMap<>();
	private int nextNumber;

	/** An active account with these roles. */
	long account(String username, String... roleCodes) {
		root().update("INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)", username, PASSWORD_HASH,
				username + "@example.com");
		long id = root().queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
		for (String code : roleCodes) {
			root().update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, code);
		}
		users.put(id, new CurrentUser(id, username, List.of(roleCodes)));
		return id;
	}

	/** The user as a request of theirs carries them, with the roles they were made with. */
	CurrentUser as(long id) {
		return users.get(id);
	}

	long systemIdentity() {
		return root().queryForObject("SELECT id FROM users WHERE is_system = TRUE", Long.class);
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

	// The endpoints

	ResultActions getAs(String username, String path) throws Exception {
		return mvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token(username)));
	}

	ResultActions postAs(String username, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1" + path).header("Authorization", "Bearer " + token(username)).contentType(MediaType.APPLICATION_JSON)
				.content(json == null ? "{}" : json));
	}

	/** Opens a ticket through the endpoint and returns its id. */
	long open(String username, String json) throws Exception {
		String answer = postAs(username, "/tickets", json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(answer, "$.id")).longValue();
	}

	/** Opens an ordinary ticket of type OTHER through the service. */
	long other(long creatorId, String title) {
		return tickets.create(as(creatorId), "OTHER", title, "Something needs someone else", List.of(), null, null, null).ticket().id();
	}

	/** Opens a ticket on items through the service. */
	long onItems(long creatorId, Long... inventoryIds) {
		return tickets.create(as(creatorId), "INVENTORY_EXCEPTION", "Count is off", "The count disagrees", List.of(inventoryIds), null, null, null)
				.ticket().id();
	}

	// Rows made directly

	/** An item of the warehouse, not deleted. */
	long item(String name, long createdBy) {
		root().update("INSERT INTO inventory (sku, name, available_quantity, created_by) VALUES (?, ?, 10, ?)", number('S'), name, createdBy);
		return root().queryForObject("SELECT id FROM inventory WHERE name = ? AND deleted_at IS NULL", Long.class, name);
	}

	/**
	 * A ticket in any status, inserted directly, with the fields that status needs.
	 *
	 * @param assigneeId the assignee, for IN_PROGRESS and after
	 * @param resolvedBy who resolved it, for RESOLVED and CLOSED
	 */
	long ticketRow(String status, long createdBy, Long assigneeId, Long resolvedBy) {
		String number = number('T');
		boolean closed = "CLOSED".equals(status);
		boolean cancelled = "CANCELLED".equals(status);
		root().update("""
				INSERT INTO tickets (ticket_no, created_by, assignee_id, assigned_at, resolved_by, resolution, resolved_at, closed_by, closed_at,
				                     cancelled_by, cancel_reason, cancelled_at, type, source, status, title, description)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OTHER', 'MANUAL', ?, 'Made for a test', 'Made for a test')
				""", number, createdBy, assigneeId, assigneeId == null ? null : LONG_AGO, resolvedBy, resolvedBy == null ? null : "done", resolvedBy == null ? null : LONG_AGO,
				closed ? createdBy : null, closed ? LONG_AGO : null, cancelled ? createdBy : null, cancelled ? "made" : null,
				cancelled ? LONG_AGO : null, status);
		return root().queryForObject("SELECT id FROM tickets WHERE ticket_no = ?", Long.class, number);
	}

	String numberOf(long ticketId) {
		return root().queryForObject("SELECT ticket_no FROM tickets WHERE id = ?", String.class, ticketId);
	}

	private String number(char prefix) {
		return String.format("%c%07d", prefix, ++nextNumber);
	}

	// The rows

	Map<String, Object> ticket(long id) {
		return root().queryForMap("SELECT * FROM tickets WHERE id = ?", id);
	}

	String statusOf(long id) {
		return root().queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, id);
	}

	Long assigneeOf(long id) {
		return root().queryForObject("SELECT assignee_id FROM tickets WHERE id = ?", Long.class, id);
	}

	List<Map<String, Object>> history(long ticketId) {
		return root().queryForList("SELECT from_user_id, to_user_id, changed_by, reason FROM ticket_assignment_history WHERE ticket_id = ? ORDER BY id",
				ticketId);
	}

	List<Map<String, Object>> logs(long ticketId) {
		return root().queryForList("""
				SELECT action, category, operator_id, CAST(before_data AS CHAR) AS before_data, CAST(after_data AS CHAR) AS after_data, remark
				FROM operation_logs WHERE target_type = 'TICKET' AND target_id = ? ORDER BY id
				""", ticketId);
	}

	int count(String sql, Object... args) {
		return root().queryForObject(sql, Integer.class, args);
	}

}
