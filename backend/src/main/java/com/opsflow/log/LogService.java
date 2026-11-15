package com.opsflow.log;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.time.DayRanges;
import com.opsflow.common.time.DayRanges.Range;
import com.opsflow.common.validation.Limits;
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

	public PageResponse<OrderRecord> orderRecords(Long productId, String orderNo, String from, String to, PageQuery page) {
		String number = orderNo(orderNo, productId, from, to);
		Range range = days.parse(from, to);
		return PageResponse.of(repository.orderRecords(id("productId", productId), number, range, page), page,
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
