package com.opsflow.common.paging;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;

/**
 * Checks the paging parameters of a list request and makes a {@link PageQuery}. A parameter that is out
 * of range or unknown is refused with {@code VALIDATION_FAILED}, never corrected silently.
 */
@Component
public class PageQueryFactory {

	private final int pageSize;

	public PageQueryFactory(OpsflowProperties properties) {
		this.pageSize = properties.paging().pageSize();
	}

	/**
	 * @param limit the {@code limit} parameter, or {@code null} for the page size
	 * @param cursor the {@code cursor} parameter, or {@code null} for the first page
	 * @param direction the {@code direction} parameter, or {@code null} for {@code next}
	 * @param order the {@code order} parameter, or {@code null} for {@code desc}
	 * @param orderChoosable whether this list lets the user choose the order (the ticket lists, T-37)
	 */
	public PageQuery create(Integer limit, String cursor, String direction, String order, boolean orderChoosable) {
		int size = limit == null ? pageSize : limit;
		if (size < 1 || size > pageSize) {
			throw ApiException.invalid("limit", "must be between 1 and " + pageSize);
		}
		Direction travel = direction == null ? Direction.NEXT : parse(Direction.class, "direction", direction);
		if (order != null && !orderChoosable) {
			throw ApiException.invalid("order", "is not allowed on this list");
		}
		SortOrder sort = order == null ? SortOrder.DESC : parse(SortOrder.class, "order", order);
		Cursor position = cursor == null ? null : CursorCodec.decode(cursor);
		if (position == null && travel == Direction.PREV) {
			throw ApiException.invalid("direction", "'prev' needs a cursor");
		}
		return new PageQuery(size, position, travel, sort);
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String field, String value) {
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.invalid(field, "is not a known value");
		}
	}

}
