package com.opsflow.log;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.paging.SortOrder;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.RequiresPermission;

/**
 * The product log, the warehouse log, the Deleted items module, and the product picker. See Logs in
 * docs/api/api-design.md. Every list here only reads.
 */
@RestController
public class LogController {

	public record ListingRecordResponse(long id, Instant at, long productId, long inventoryId, String action, String name, String price,
			String by) {
	}

	public record OrderRecordResponse(long id, Instant at, long productId, String productName, String orderNo, String orderedBy, int quantity,
			String result, int stockDeducted) {
	}

	public record ItemRecordResponse(long id, Instant at, long inventoryId, String action, String sku, String name, int quantity, String by) {
	}

	/** @param amount signed: positive into the stock, negative out of it */
	public record StockMovementResponse(long id, Instant at, long inventoryId, String sku, String itemName, String type, String orderNo,
			String reason, int amount, String by) {
	}

	/** @param productId the product of the item if it ever had one, for the two parts of its own log that belong to the product */
	public record DeletedItemResponse(long id, String sku, String name, Instant deletedAt, String deletedBy, Long productId) {
	}

	public record ProductOptionResponse(long id, String name, String sku) {
	}

	private final LogService logs;
	private final PageQueryFactory pages;

	LogController(LogService logs, PageQueryFactory pages) {
		this.logs = logs;
		this.pages = pages;
	}

	@RequiresPermission(Permission.PRODUCT_VIEW)
	@GetMapping("/listing-records")
	public PageResponse<ListingRecordResponse> listingRecords(@RequestParam(required = false) Long productId,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.create(limit, cursor, direction, order, true);
		return map(logs.listingRecords(productId, from, to, page), r -> new ListingRecordResponse(r.id(), r.at(), r.productId(),
				r.inventoryId(), r.action(), r.name(), r.price().toPlainString(), r.by()));
	}

	@RequiresPermission(Permission.PRODUCT_VIEW)
	@GetMapping("/order-records")
	public PageResponse<OrderRecordResponse> orderRecords(@RequestParam(required = false) Long productId,
			@RequestParam(required = false) String orderNo, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) String direction,
			@RequestParam(required = false) String order) {
		PageQuery page = pages.create(limit, cursor, direction, order, true);
		return map(logs.orderRecords(productId, orderNo, from, to, page), r -> new OrderRecordResponse(r.id(), r.at(), r.productId(),
				r.productName(), r.orderNo(), r.orderedBy(), r.quantity(), r.result(), r.stockDeducted()));
	}

	@RequiresPermission(Permission.STOCK_VIEW)
	@GetMapping("/item-records")
	public PageResponse<ItemRecordResponse> itemRecords(@RequestParam(required = false) Long inventoryId,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.create(limit, cursor, direction, order, true);
		return map(logs.itemRecords(inventoryId, from, to, page), r -> new ItemRecordResponse(r.id(), r.at(), r.inventoryId(), r.action(),
				r.sku(), r.name(), r.quantity(), r.by()));
	}

	@RequiresPermission(Permission.STOCK_VIEW)
	@GetMapping("/stock-movements")
	public PageResponse<StockMovementResponse> stockMovements(@RequestParam(required = false) Long inventoryId,
			@RequestParam(required = false) String orderNo, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) String direction,
			@RequestParam(required = false) String order) {
		PageQuery page = pages.create(limit, cursor, direction, order, true);
		return map(logs.stockMovements(inventoryId, orderNo, from, to, page), r -> new StockMovementResponse(r.id(), r.at(),
				r.inventoryId(), r.sku(), r.itemName(), r.type(), r.orderNo(), r.reason(), r.amount(), r.by()));
	}

	/** Newest deletion first; the order is fixed. */
	@RequiresPermission(Permission.DELETED_ITEMS_VIEW)
	@GetMapping("/deleted-items")
	public PageResponse<DeletedItemResponse> deletedItems(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.DESC);
		return map(logs.deletedItems(search, page), r -> new DeletedItemResponse(r.id(), r.sku(), r.name(), r.deletedAt(), r.deletedBy(),
				r.productId()));
	}

	/** By name from A to Z; the order is fixed. */
	@RequiresPermission(Permission.PRODUCT_VIEW)
	@GetMapping("/product-options")
	public PageResponse<ProductOptionResponse> productOptions(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.ASC);
		return map(logs.productOptions(search, page), r -> new ProductOptionResponse(r.id(), r.name(), r.sku()));
	}

	private static <T, R> PageResponse<R> map(PageResponse<T> found, Function<T, R> mapper) {
		List<R> items = found.items().stream().map(mapper).toList();
		return new PageResponse<>(items, found.nextCursor(), found.prevCursor());
	}

}
