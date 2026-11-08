package com.opsflow.common.paging;

/**
 * The position of a row in a list: the value of its sort key, as text, and its {@code id}. The id breaks
 * ties between rows with the same key, so two rows are never confused and none is skipped.
 */
public record Cursor(String key, long id) {

	public Cursor {
		if (key == null) {
			throw new IllegalArgumentException("A cursor needs a sort key");
		}
	}

}
