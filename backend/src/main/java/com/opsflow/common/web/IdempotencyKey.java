package com.opsflow.common.web;

import java.util.Optional;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.validation.Limits;

/**
 * The {@code Idempotency-Key} header of an order request. A repeated submission with the same key
 * returns the original order and adds no row (see Idempotency in docs/api/api-design.md).
 *
 * A controller reads the header with {@code @RequestHeader(value = IdempotencyKey.HEADER, required =
 * false)} and passes it to {@link #parse}. The key is stored in {@code orders.idempotency_key}, a
 * {@value Limits#IDEMPOTENCY_KEY}-character column, so a longer one is refused. Only visible ASCII
 * characters are accepted, which fits a UUID and any other common key and keeps the stored text exact.
 */
public final class IdempotencyKey {

	public static final String HEADER = "Idempotency-Key";

	private IdempotencyKey() {
	}

	/**
	 * @param header the header value, or {@code null} when the request has none
	 * @return the key, or empty when the request carries none
	 * @throws ApiException {@code VALIDATION_FAILED} when the header is present but empty, too long, or not visible ASCII
	 */
	public static Optional<String> parse(String header) {
		if (header == null) {
			return Optional.empty();
		}
		if (header.isEmpty() || header.length() > Limits.IDEMPOTENCY_KEY) {
			throw ApiException.invalid(HEADER, "must be 1 to " + Limits.IDEMPOTENCY_KEY + " characters");
		}
		for (int i = 0; i < header.length(); i++) {
			char c = header.charAt(i);
			if (c < 0x21 || c > 0x7E) {
				throw ApiException.invalid(HEADER, "may only hold visible ASCII characters");
			}
		}
		return Optional.of(header);
	}

}
