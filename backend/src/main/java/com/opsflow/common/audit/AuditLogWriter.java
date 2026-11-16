package com.opsflow.common.audit;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opsflow.common.time.UtcTime;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the rows of {@code operation_logs}. See Auditing in docs/database/database-design.md.
 *
 * A log row is written in the same transaction as the change it describes, so that neither exists
 * without the other. The writer therefore refuses to run outside a transaction.
 *
 * The time of a row is taken from the clock of the application, as the times of the changes are, so that
 * the audit page and the time filter agree with them even when the clock of the database differs.
 *
 * The data of a row must never hold a password, a code, or a hash. A time is passed as its ISO 8601
 * text ({@code Instant.toString()}).
 */
@Component
public class AuditLogWriter {

	/** The {@code target_type} of a row about a user account. */
	public static final String TARGET_USER = "USER";
	/** A warehouse item: the target id is the {@code inventory} id. */
	public static final String TARGET_ITEM = "ITEM";
	/** A product: the target id is the {@code products} id. */
	public static final String TARGET_PRODUCT = "PRODUCT";
	/** A ticket: the target id is the {@code tickets} id. */
	public static final String TARGET_TICKET = "TICKET";

	private static final String INSERT = """
			INSERT INTO operation_logs (operator_id, category, action, target_type, target_id, before_data, after_data, ip_address, remark,
			                            created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock;

	public AuditLogWriter(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * @param operatorId the user who did it, or null when nobody is logged in or the program acts by itself
	 * @param before the state before the change, or null
	 * @param after the state after the change with the label of the row, or null
	 * @param ipAddress the address of the request when the web layer knows it, or null
	 */
	public void write(AuditAction action, Long operatorId, String targetType, long targetId, Map<String, ?> before,
			Map<String, ?> after, String ipAddress, String remark) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("An audit row must be written in the transaction of the change it describes");
		}
		jdbc.update(INSERT, operatorId, action.category().name(), action.name(), targetType, targetId, toJson(before),
				toJson(after), ipAddress, remark, UtcTime.toLocalDateTime(clock.instant().truncatedTo(ChronoUnit.MILLIS)));
	}

	private String toJson(Map<String, ?> data) {
		return data == null ? null : json.writeValueAsString(data);
	}

}
