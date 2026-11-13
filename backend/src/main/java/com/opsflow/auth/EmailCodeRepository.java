package com.opsflow.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.opsflow.common.time.UtcTime;

/** The SQL on {@code email_verification_codes} and {@code email_code_request_counts}. */
@Repository
class EmailCodeRepository {

	/** A code that has not been used. Whether it is still valid is decided from the two last fields. */
	record Code(long id, String email, String codeHash, int attempts, Instant expiresAt) {
	}

	private final JdbcTemplate jdbc;

	EmailCodeRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Adds 1 to the count of a window and returns the new count. The statement is atomic and leaves the
	 * row locked until the transaction ends, so two requests at the same moment both count, one after
	 * the other.
	 */
	int countRequest(String scope, Instant windowStart) {
		LocalDateTime start = UtcTime.toLocalDateTime(windowStart);
		jdbc.update("""
				INSERT INTO email_code_request_counts (scope, window_start, request_count) VALUES (?, ?, 1)
				ON DUPLICATE KEY UPDATE request_count = request_count + 1
				""", scope, start);
		return jdbc.queryForObject("SELECT request_count FROM email_code_request_counts WHERE scope = ? AND window_start = ?",
				Integer.class, scope, start);
	}

	boolean requestedAfter(String email, EmailCodePurpose purpose, Instant after) {
		return jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM email_verification_codes WHERE email = ? AND purpose = ? AND created_at > ?)",
				Boolean.class, email, purpose.name(), UtcTime.toLocalDateTime(after));
	}

	/** Only the latest code works: the earlier ones of the same email and purpose are marked used. */
	void consumeAll(String email, EmailCodePurpose purpose, Instant at) {
		jdbc.update("UPDATE email_verification_codes SET consumed_at = ? WHERE email = ? AND purpose = ? AND consumed_at IS NULL",
				UtcTime.toLocalDateTime(at), email, purpose.name());
	}

	void insert(String email, EmailCodePurpose purpose, String codeHash, Instant createdAt, Instant expiresAt) {
		jdbc.update("INSERT INTO email_verification_codes (email, purpose, code_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
				email, purpose.name(), codeHash, UtcTime.toLocalDateTime(expiresAt), UtcTime.toLocalDateTime(createdAt));
	}

	Optional<Code> findUnused(String email, EmailCodePurpose purpose) {
		return jdbc.query("""
				SELECT id, email, code_hash, attempts, expires_at
				FROM email_verification_codes
				WHERE email = ? AND purpose = ? AND consumed_at IS NULL
				ORDER BY created_at DESC, id DESC
				LIMIT 1
				""", (rs, row) -> new Code(rs.getLong("id"), rs.getString("email"), rs.getString("code_hash"), rs.getInt("attempts"),
				UtcTime.toInstant(rs.getObject("expires_at", LocalDateTime.class))), email, purpose.name()).stream().findFirst();
	}

	/** Atomic, so wrong entries at the same moment all count. */
	void addWrongEntry(long id) {
		jdbc.update("UPDATE email_verification_codes SET attempts = attempts + 1 WHERE id = ? AND consumed_at IS NULL", id);
	}

	/**
	 * Uses the code, if it can still be used. Only one of two requests at the same moment updates the row.
	 *
	 * @return whether this call used it
	 */
	boolean consume(long id, Instant at, int maxWrongEntries) {
		LocalDateTime now = UtcTime.toLocalDateTime(at);
		return jdbc.update("""
				UPDATE email_verification_codes SET consumed_at = ?
				WHERE id = ? AND consumed_at IS NULL AND expires_at > ? AND attempts < ?
				""", now, id, now, maxWrongEntries) == 1;
	}

	/**
	 * Deletes codes that have expired, at most {@code limit} of them. The rows are found with a plain
	 * read and deleted by id, so the delete locks those rows only and not the ranges it would scan.
	 */
	int deleteExpiredCodes(Instant now, int limit) {
		LocalDateTime time = UtcTime.toLocalDateTime(now);
		List<Long> ids = jdbc.queryForList("SELECT id FROM email_verification_codes WHERE expires_at < ? ORDER BY id LIMIT ?",
				Long.class, time, limit);
		int deleted = 0;
		for (Long id : ids) {
			deleted += jdbc.update("DELETE FROM email_verification_codes WHERE id = ? AND expires_at < ?", id, time);
		}
		return deleted;
	}

	/** Deletes the counts of windows that started before {@code before}, at most {@code limit} of them. */
	int deleteCountsBefore(Instant before, int limit) {
		List<Long> ids = jdbc.queryForList("SELECT id FROM email_code_request_counts WHERE window_start < ? ORDER BY id LIMIT ?",
				Long.class, UtcTime.toLocalDateTime(before), limit);
		int deleted = 0;
		for (Long id : ids) {
			deleted += jdbc.update("DELETE FROM email_code_request_counts WHERE id = ?", id);
		}
		return deleted;
	}

}
