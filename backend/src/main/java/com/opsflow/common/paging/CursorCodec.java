package com.opsflow.common.paging;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.opsflow.common.error.ApiException;

/**
 * Turns a {@link Cursor} into the opaque string of the API and back. The client never builds or reads
 * one (see Paging in docs/api/api-design.md).
 *
 * The text is URL-safe Base64 without padding, over {@code <id>:<key>}. The id comes first, so the key
 * may hold any character. A cursor is not signed: it carries no right, and the key it names is always
 * passed to the database as a parameter. A string that does not decode is refused with
 * {@code VALIDATION_FAILED}.
 */
public final class CursorCodec {

	private static final String FIELD = "cursor";
	private static final int MAX_LENGTH = 1024;

	private CursorCodec() {
	}

	public static String encode(Cursor cursor) {
		String text = cursor.id() + ":" + cursor.key();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	public static Cursor decode(String encoded) {
		if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_LENGTH) {
			throw ApiException.invalid(FIELD, "is not a valid cursor");
		}
		try {
			String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int colon = text.indexOf(':');
			if (colon < 1) {
				throw ApiException.invalid(FIELD, "is not a valid cursor");
			}
			long id = Long.parseLong(text.substring(0, colon));
			if (id < 0) {
				throw ApiException.invalid(FIELD, "is not a valid cursor");
			}
			return new Cursor(text.substring(colon + 1), id);
		}
		catch (IllegalArgumentException e) {
			// Base64 error or a number that is not a number
			throw ApiException.invalid(FIELD, "is not a valid cursor");
		}
	}

}
