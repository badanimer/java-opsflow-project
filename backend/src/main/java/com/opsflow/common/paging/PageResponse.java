package com.opsflow.common.paging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A page of a list: the rows, and a cursor for each direction in which another page exists. A cursor is
 * {@code null} when there is no page that way. There is no total count.
 */
public record PageResponse<T>(List<T> items, String nextCursor, String prevCursor) {

	/**
	 * Builds the response from the rows a repository read for a {@link PageQuery}.
	 *
	 * @param fetched at most {@code query.fetchSize()} rows, in the order the repository read them. For
	 *     {@code PREV} that is the reverse of the order shown, which this method puts right
	 * @param cursorOf the cursor of a row: its sort key and id
	 */
	public static <T> PageResponse<T> of(List<T> fetched, PageQuery query, Function<T, Cursor> cursorOf) {
		boolean more = fetched.size() > query.limit();
		List<T> page = new ArrayList<>(more ? fetched.subList(0, query.limit()) : fetched);
		if (query.direction() == Direction.PREV) {
			Collections.reverse(page);
		}
		if (page.isEmpty()) {
			return new PageResponse<>(page, null, null);
		}
		// Going next, a previous page exists when the client came from one; going previous, a next page exists
		// for the same reason. The extra row tells whether there is one more in the direction of travel.
		boolean cameFrom = query.cursor() != null;
		boolean hasNext = query.direction() == Direction.NEXT ? more : cameFrom;
		boolean hasPrev = query.direction() == Direction.NEXT ? cameFrom : more;
		String next = hasNext ? CursorCodec.encode(cursorOf.apply(page.get(page.size() - 1))) : null;
		String prev = hasPrev ? CursorCodec.encode(cursorOf.apply(page.get(0))) : null;
		return new PageResponse<>(page, next, prev);
	}

}
