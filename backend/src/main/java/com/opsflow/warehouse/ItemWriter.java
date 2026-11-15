package com.opsflow.warehouse;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.warehouse.WarehouseRepository.ItemRow;
import com.opsflow.warehouse.WarehouseRepository.ProductRow;

/**
 * The transactions of a warehouse item: adding, editing, and deleting it (T-39; see Warehouse items and
 * the Warehouse items rows of Concurrency by Module in docs/database/database-design.md). Each runs in
 * one transaction and is retried by its caller on a deadlock. Lock order: {@code products}, then
 * {@code inventory}. A time that is written is taken after the locks are held.
 */
@Component
public class ItemWriter {

	static final String INITIAL_STOCK_REASON = "initial stock";

	/** A generated SKU that was issued before: the caller makes another and tries again. */
	static final class SkuTaken extends RuntimeException {

		private static final long serialVersionUID = 1L;

		SkuTaken() {
			super("The generated SKU was issued before", null, false, false);
		}
	}

	private final WarehouseRepository repository;
	private final AuditLogWriter audit;
	private final Clock clock;

	ItemWriter(WarehouseRepository repository, AuditLogWriter audit, Clock clock) {
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Adds an item with its starting stock. The unique keys decide a repeated name or SKU, also against
	 * an addition that has not committed yet: the insert waits for it and then fails.
	 */
	@Transactional
	public ItemRow create(long actorId, String sku, String name, String imagePath, int startingQuantity, String ipAddress) {
		long id;
		try {
			id = repository.insertItem(sku, name, imagePath, startingQuantity, actorId);
		}
		catch (DuplicateKeyException duplicate) {
			if (String.valueOf(duplicate.getMessage()).contains("uk_inventory_sku")) {
				throw new SkuTaken();
			}
			throw nameTaken();
		}
		if (startingQuantity > 0) {
			repository.insertTransaction(id, "RESTOCK", startingQuantity, 0, startingQuantity, INITIAL_STOCK_REASON, actorId);
		}
		repository.insertItemEvent(id, "CREATED", sku, name, startingQuantity, actorId);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("sku", sku);
		after.put("name", name);
		after.put("imageUrl", ImageStore.urlOf(imagePath));
		after.put("quantity", startingQuantity);
		audit.write(AuditAction.ITEM_CREATED, actorId, AuditLogWriter.TARGET_ITEM, id, null, after, ipAddress, null);
		return new ItemRow(id, sku, name, imagePath, startingQuantity, false);
	}

	/**
	 * Changes the name and the image of an item that is not on sale. The product row is locked first, as a
	 * listing locks it, so an edit and a listing of the same item run one after the other.
	 *
	 * @return the item as it is now, and whether it is listed (always false after a change)
	 */
	@Transactional
	public ItemRow edit(long actorId, long itemId, String name, String imagePath, String ipAddress) {
		ProductRow product = repository.lockProductOfItem(itemId).orElse(null);
		ItemRow item = repository.lockItem(itemId).filter(found -> !found.deleted()).orElseThrow(ItemWriter::itemGone);
		if (product != null && product.listed()) {
			throw new ApiException(ErrorCode.ITEM_LISTED, "The item is on sale. Delist it to change its name or image.");
		}
		boolean nameChanged = !item.name().equals(name);
		boolean imageChanged = !Objects.equals(item.imagePath(), imagePath);
		if (!nameChanged && !imageChanged) {
			return item;
		}
		try {
			repository.updateItem(itemId, name, imagePath);
		}
		catch (DuplicateKeyException duplicate) {
			throw nameTaken();
		}
		Map<String, Object> before = new LinkedHashMap<>();
		Map<String, Object> after = new LinkedHashMap<>();
		if (nameChanged) {
			before.put("name", item.name());
			after.put("name", name);
		}
		if (imageChanged) {
			before.put("imageUrl", ImageStore.urlOf(item.imagePath()));
			after.put("imageUrl", ImageStore.urlOf(imagePath));
		}
		after.put("sku", item.sku());
		if (!nameChanged) {
			after.put("name", name);
		}
		audit.write(AuditAction.ITEM_UPDATED, actorId, AuditLogWriter.TARGET_ITEM, itemId, before, after, ipAddress, null);
		return new ItemRow(itemId, item.sku(), name, imagePath, item.availableQuantity(), false);
	}

	/**
	 * Deletes an item softly: its product is delisted when it is on sale, the item is marked deleted, and
	 * its stock is left as it is. A second deletion waits for the first and finds the item deleted.
	 */
	@Transactional
	public void delete(long actorId, long itemId, String ipAddress) {
		ProductRow product = repository.lockProductOfItem(itemId).orElse(null);
		ItemRow item = repository.lockItem(itemId).filter(found -> !found.deleted()).orElseThrow(ItemWriter::itemGone);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		String remark = "ITEM_DELETED:" + itemId;

		if (product != null && product.listed()) {
			repository.delist(product.id(), now, actorId);
			repository.insertListingEvent(product.id(), itemId, "DELISTED", product.name(), product.price(), actorId);
			Map<String, Object> after = new LinkedHashMap<>();
			after.put("status", "UNLISTED");
			after.put("cancelledTicketIds", List.of());
			after.put("name", product.name());
			after.put("sku", item.sku());
			audit.write(AuditAction.PRODUCT_DELISTED, actorId, AuditLogWriter.TARGET_PRODUCT, product.id(), Map.of("status", "LISTED"),
					after, ipAddress, remark);
		}
		// Phase 6 (T-21, T-33): cancel here every OPEN or IN_PROGRESS ticket linked to the item, as the SYSTEM
		// identity, with the reason ITEM_DELETED: and the item id, and log each as TICKET_CANCELLED.

		repository.softDeleteItem(itemId, now, actorId);
		repository.insertItemEvent(itemId, "DELETED", item.sku(), item.name(), item.availableQuantity(), actorId);
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("name", item.name());
		before.put("availableQuantity", item.availableQuantity());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("deleted", true);
		after.put("cancelledTicketIds", List.of());
		after.put("sku", item.sku());
		after.put("name", item.name());
		audit.write(AuditAction.ITEM_DELETED, actorId, AuditLogWriter.TARGET_ITEM, itemId, before, after, ipAddress, null);
	}

	static ApiException itemGone() {
		return new ApiException(ErrorCode.NOT_FOUND, "The item no longer exists");
	}

	private static ApiException nameTaken() {
		return new ApiException(ErrorCode.ITEM_NAME_TAKEN, "Another item already has this name");
	}

}
