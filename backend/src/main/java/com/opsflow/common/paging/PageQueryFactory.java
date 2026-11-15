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
	private final int productPageSize;
	private final int productPageMax;

	public PageQueryFactory(OpsflowProperties properties) {
		this.pageSize = properties.paging().pageSize();
		this.productPageSize = properties.paging().productPageSize();
		this.productPageMax = properties.paging().productPageMax();
	}

	/**
	 * @param limit the {@code limit} parameter, or {@code null} for the page size
	 * @param cursor the {@code cursor} parameter, or {@code null} for the first page
	 * @param direction the {@code direction} parameter, or {@code null} for {@code next}
	 * @param order the {@code order} parameter, or {@code null} for {@code desc}
	 * @param orderChoosable whether this list lets the user choose the order (the ticket lists, T-37)
	 */
	public PageQuery create(Integer limit, String cursor, String direction, String order, boolean orderChoosable) {
		return build(limit, cursor, direction, order, orderChoosable, SortOrder.DESC);
	}

	/**
	 * For a list whose order is fixed and is not the default {@code desc}, such as the user page, which is
	 * ordered by username from A to Z. An {@code order} parameter is refused, as on every list whose order is fixed.
	 *
	 * @param listOrder the one order of this list
	 */
	public PageQuery createInOrder(Integer limit, String cursor, String direction, String order, SortOrder listOrder) {
		return build(limit, cursor, direction, order, false, listOrder);
	}

	/**
	 * For the product page, ordered by name: {@code opsflow.paging.product-page-size} cards by default, and at
	 * most {@code product-page-max}, because the person chooses how many cards a page shows (Home page and
	 * paging in the database design).
	 */
	public PageQuery createProductPage(Integer limit, String cursor, String direction, String order) {
		return build(limit, cursor, direction, order, false, SortOrder.ASC, productPageSize, productPageMax);
	}

	private PageQuery build(Integer limit, String cursor, String direction, String order, boolean orderChoosable,
			SortOrder defaultOrder) {
		return build(limit, cursor, direction, order, orderChoosable, defaultOrder, pageSize, pageSize);
	}

	private PageQuery build(Integer limit, String cursor, String direction, String order, boolean orderChoosable,
			SortOrder defaultOrder, int defaultSize, int maxSize) {
		int size = limit == null ? defaultSize : limit;
		if (size < 1 || size > maxSize) {
			throw ApiException.invalid("limit", "must be between 1 and " + maxSize);
		}
		Direction travel = direction == null ? Direction.NEXT : parse(Direction.class, "direction", direction);
		if (order != null && !orderChoosable) {
			throw ApiException.invalid("order", "is not allowed on this list");
		}
		SortOrder sort = order == null ? defaultOrder : parse(SortOrder.class, "order", order);
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
