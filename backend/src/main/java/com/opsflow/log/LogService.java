package com.opsflow.log;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditCategory;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.time.DayRanges;
import com.opsflow.common.time.DayRanges.Range;
import com.opsflow.common.validation.Limits;
import com.opsflow.log.LogRepository.AuditRecord;
import com.opsflow.log.LogRepository.DeletedItem;
import com.opsflow.log.LogRepository.ItemRecord;
import com.opsflow.log.LogRepository.ListingRecord;
import com.opsflow.log.LogRepository.OrderRecord;
import com.opsflow.log.LogRepository.ProductOption;
import com.opsflow.log.LogRepository.StockMovement;

/**
 * The two logs, the Deleted items module, and the product picker (T-46, T-50; see Logs in
 * docs/database/database-design.md). It checks the filters and reads; it changes nothing.
 *
 * Each log takes the chosen product or item, the time filter, and the direction. The order records and the
 * stock movements can instead be found by one whole order number, which is used alone: with any other
 * filter it is refused.
 */
@Service
public class LogService {

	/** The results of an order attempt, as {@code chk_order_attempts_result} allows them. */
	private static final Set<String> RESULTS = Set.of("COMPLETED", "PRODUCT_DELISTED", "PRODUCT_CHANGED", "OUT_OF_STOCK", "INVALID_QUANTITY",
			"BUSY");

	private final LogRepository repository;
	private final DayRanges days;

	LogService(LogRepository repository, DayRanges days) {
		this.repository = repository;
		this.days = days;
	}

	public PageResponse<ListingRecord> listingRecords(Long productId, String from, String to, PageQuery page) {
		Range range = days.parse(from, to);
		return PageResponse.of(repository.listingRecords(id("productId", productId), range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	/**
	 * The order records. With {@link Permission#ORDER_VIEW_ALL} they are everyone's, and {@code userId} narrows
	 * them to one person; without it they are the caller's own, and {@code userId} is refused, as on the ticket
	 * screens (Q-34).
	 */
	public PageResponse<OrderRecord> orderRecords(CurrentUser user, Long productId, Long userId, String orderNo, String from, String to,
			PageQuery page) {
		boolean all = user.can(Permission.ORDER_VIEW_ALL);
		if (userId != null && !all) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Your roles show only your own order records");
		}
		Long orderedBy = all ? id("userId", userId) : Long.valueOf(user.id());
		String number = orderNo(orderNo, productId, from, to);
		if (number != null && userId != null) {
			throw ApiException.invalid("orderNo", "is used alone, with no other filter");
		}
		Range range = days.parse(from, to);
		return PageResponse.of(repository.orderRecords(id("productId", productId), orderedBy, number, range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	public PageResponse<ItemRecord> itemRecords(Long inventoryId, String from, String to, PageQuery page) {
		Range range = days.parse(from, to);
		return PageResponse.of(repository.itemRecords(id("inventoryId", inventoryId), range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	public PageResponse<StockMovement> stockMovements(Long inventoryId, String orderNo, String from, String to, PageQuery page) {
		String number = orderNo(orderNo, inventoryId, from, to);
		Range range = days.parse(from, to);
		return PageResponse.of(repository.stockMovements(id("inventoryId", inventoryId), number, range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	public PageResponse<DeletedItem> deletedItems(String search, PageQuery page) {
		return PageResponse.of(repository.deletedItems(search(search), page), page,
				r -> new Cursor(LogRepository.timeKey(r.deletedAt()), r.id()));
	}

	public PageResponse<ProductOption> productOptions(String search, PageQuery page) {
		return PageResponse.of(repository.productOptions(search(search), page), page, r -> new Cursor(r.name(), r.id()));
	}

	/**
	 * One area of the audit page (see Audit log page in docs/database/database-design.md): the rows of one
	 * category, with the action, the time filter, and the user, or {@code system} for the actions of the system.
	 */
	public PageResponse<AuditRecord> auditRecords(String area, String action, Long userId, Boolean system, Long aboutUserId, String from,
			String to, PageQuery page) {
		AuditCategory category = category(area);
		String checkedAction = null;
		if (action != null && !action.isBlank()) {
			String name = action.strip().toUpperCase(Locale.ROOT);
			checkedAction = Arrays.stream(AuditAction.values()).filter(a -> a.category() == category && a.name().equals(name)).findFirst()
					.map(AuditAction::name)
					.orElseThrow(() -> ApiException.invalid("action", "must be an action of the area " + category.name()));
		}
		boolean systemOnly = Boolean.TRUE.equals(system);
		if (systemOnly && userId != null) {
			throw ApiException.invalid("system", "is used instead of a user, not with one");
		}
		if (aboutUserId != null && category != AuditCategory.ACCOUNT && category != AuditCategory.ROLE) {
			throw ApiException.invalid("aboutUserId", "is for the areas ACCOUNT and ROLE, whose rows are about a user");
		}
		Range range = days.parse(from, to);
		return PageResponse.of(repository.auditRecords(category.name(), checkedAction, id("userId", userId), systemOnly,
				id("aboutUserId", aboutUserId), range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	/** The Orders area of the audit page: the attempts, with the result, the time filter, and the user who ordered. */
	public PageResponse<OrderRecord> auditOrderRecords(String result, Long userId, String from, String to, PageQuery page) {
		String checkedResult = null;
		if (result != null && !result.isBlank()) {
			checkedResult = result.strip().toUpperCase(Locale.ROOT);
			if (!RESULTS.contains(checkedResult)) {
				throw ApiException.invalid("result", "must be COMPLETED, PRODUCT_DELISTED, PRODUCT_CHANGED, OUT_OF_STOCK, INVALID_QUANTITY, or BUSY");
			}
		}
		Range range = days.parse(from, to);
		return PageResponse.of(repository.auditOrderRecords(checkedResult, id("userId", userId), range, page), page,
				r -> new Cursor(LogRepository.timeKey(r.at()), r.id()));
	}

	private static AuditCategory category(String area) {
		if (area == null || area.isBlank()) {
			throw ApiException.invalid("area", "is required: ACCOUNT, ROLE, TICKET, STOCK, ITEM, or PRODUCT");
		}
		try {
			return AuditCategory.valueOf(area.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException unknown) {
			throw ApiException.invalid("area", "must be ACCOUNT, ROLE, TICKET, STOCK, ITEM, or PRODUCT");
		}
	}

	private static Long id(String field, Long id) {
		if (id != null && id < 1) {
			throw ApiException.invalid(field, "must be a positive id");
		}
		return id;
	}

	/**
	 * One whole order number, used alone; or null. It is written as the numbers are made, whatever case was
	 * typed: {@code ORD-} in upper case and the rest in lower case, because the column compares exactly.
	 */
	private static String orderNo(String orderNo, Long chosen, String from, String to) {
		if (orderNo == null || orderNo.isBlank()) {
			return null;
		}
		String number = orderNo.strip().toLowerCase(Locale.ROOT);
		if (number.startsWith("ord-")) {
			number = "ORD-" + number.substring(4);
		}
		if (number.length() > Limits.ORDER_NO) {
			throw ApiException.invalid("orderNo", "must be at most " + Limits.ORDER_NO + " characters");
		}
		if (chosen != null || from != null || to != null) {
			throw ApiException.invalid("orderNo", "is used alone, with no other filter");
		}
		return number;
	}

	/** A part of a name or SKU to search for, or null for no search. */
	private static String search(String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		String trimmed = search.strip();
		if (trimmed.length() > Limits.ITEM_NAME) {
			throw ApiException.invalid("search", "must be at most " + Limits.ITEM_NAME + " characters");
		}
		return trimmed;
	}

}
