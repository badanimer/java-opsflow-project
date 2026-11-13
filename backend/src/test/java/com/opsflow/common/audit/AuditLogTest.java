package com.opsflow.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.opsflow.support.MySqlIntegrationTest;

/**
 * The one table of audit actions and the writer of {@code operation_logs}, against the MySQL container.
 * See Categories in docs/database/database-design.md.
 */
class AuditLogTest extends MySqlIntegrationTest {

	@Autowired
	AuditLogWriter writer;

	@Autowired
	TransactionTemplate transaction;

	/** A row cannot be filed without a category, and its action must fit the column. */
	@Test
	void everyActionHasACategoryAndFitsItsColumn() {
		Integer actionLength = jdbc.queryForObject(
				"SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = 'opsflow' AND table_name = 'operation_logs' AND column_name = 'action'",
				Integer.class);

		assertThat(AuditAction.values()).allSatisfy(action -> {
			assertThat(action.category()).as("category of %s", action).isNotNull();
			assertThat(action.name().length()).as("length of %s", action).isLessThanOrEqualTo(actionLength);
		});
	}

	/** The database refuses a category it does not know, so the two lists must be the same. */
	@Test
	void theCategoriesAreTheOnesTheDatabaseAccepts() {
		String check = root().queryForObject(
				"SELECT check_clause FROM information_schema.check_constraints WHERE constraint_schema = 'opsflow' AND constraint_name = 'chk_operation_logs_category'",
				String.class);
		List<String> inDatabase = new java.util.ArrayList<>();
		// MySQL stores the clause with its quotes escaped: _utf8mb4\'ACCOUNT\'
		java.util.regex.Matcher quoted = java.util.regex.Pattern.compile("'([A-Z_]+)\\\\?'").matcher(check);
		while (quoted.find()) {
			inDatabase.add(quoted.group(1));
		}

		assertThat(inDatabase).containsExactlyInAnyOrderElementsOf(Arrays.stream(AuditCategory.values()).map(Enum::name).toList());
		assertThat(Arrays.stream(AuditAction.values()).map(AuditAction::category).distinct())
				.as("every category has at least one action").containsExactlyInAnyOrder(AuditCategory.values());
	}

	@Test
	void aRowIsWrittenWithTheCategoryOfItsAction() {
		transaction.executeWithoutResult(status -> writer.write(AuditAction.LOGIN_LOCKED, null, AuditLogWriter.TARGET_USER, 7,
				null, Map.of("username", "alice", "failedAttempts", 5), "203.0.113.9", null));

		Map<String, Object> row = root().queryForMap(
				"SELECT operator_id, category, action, target_type, target_id, before_data, CAST(after_data AS CHAR) AS after_data, ip_address FROM operation_logs");
		assertThat(row).containsEntry("category", "ACCOUNT").containsEntry("action", "LOGIN_LOCKED")
				.containsEntry("target_type", "USER").containsEntry("target_id", 7L).containsEntry("ip_address", "203.0.113.9");
		assertThat(row.get("operator_id")).isNull();
		assertThat(row.get("before_data")).isNull();
		assertThat((String) row.get("after_data")).contains("\"username\": \"alice\"", "\"failedAttempts\": 5");
	}

	/** A log row belongs to the transaction of the change it describes. */
	@Test
	void writingOutsideATransactionIsRefused() {
		assertThatThrownBy(() -> writer.write(AuditAction.LOGIN_LOCKED, null, AuditLogWriter.TARGET_USER, 7, null, null, null, null))
				.isInstanceOf(IllegalStateException.class);

		assertThat(root().queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class)).isZero();
	}

}
