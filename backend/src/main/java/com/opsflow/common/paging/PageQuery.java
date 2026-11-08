package com.opsflow.common.paging;

/**
 * The paging part of a list request, already checked.
 *
 * @param limit the page size, at most the configured page size
 * @param cursor the edge row of the page already shown, or {@code null} for the first page
 * @param direction {@code NEXT} for the rows after the cursor in the sort order, {@code PREV} for the rows before it
 * @param order the sort direction; {@code DESC} unless the list lets the user choose
 */
public record PageQuery(int limit, Cursor cursor, Direction direction, SortOrder order) {

	/**
	 * How many rows a repository asks for. One more than the page, so that {@link PageResponse#of} can tell
	 * whether another page exists without counting.
	 */
	public int fetchSize() {
		return limit + 1;
	}

	/**
	 * True when the repository must compare with {@code >} (the rows after the cursor, ascending) and false
	 * when it must compare with {@code <}. It combines the sort order with the direction of travel: going
	 * to the previous page of a descending list reads upward.
	 */
	public boolean ascendingRead() {
		return (order == SortOrder.ASC) == (direction == Direction.NEXT);
	}

}
