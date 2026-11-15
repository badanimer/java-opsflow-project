package com.opsflow.warehouse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.warehouse.WarehouseRepository.ItemRow;

/**
 * The transaction of a manual stock change (T-30; see Manual stock changes and the Inventory row of
 * Concurrency by Module in docs/database/database-design.md): a stock-in, a write-off, or a correction.
 *
 * It locks the {@code inventory} row and nothing else, reads the stock under that lock, and refuses a
 * deleted item, a write-off larger than the stock, a stock-in past what the column holds, and a
 * correction whose stock is not the one the person saw. An order deducting the same item, a listing that
 * reads its stock, and a deletion all need the same row, so each waits for the other. The movement, its
 * exact snapshots, and the log row are written in the same transaction. Its caller retries it on a deadlock.
 */
@Component
public class StockWriter {

	/** The three manual changes, with the transaction type and the log action of each. */
	public enum Kind {
		STOCK_IN, WRITE_OFF, CORRECTION
	}

	private final WarehouseRepository repository;
	private final AuditLogWriter audit;

	StockWriter(WarehouseRepository repository, AuditLogWriter audit) {
		this.repository = repository;
		this.audit = audit;
	}

	/**
	 * @param quantity the quantity of a stock-in or a write-off, or the counted quantity of a correction
	 * @param seen the stock the person saw, for a correction; ignored otherwise
	 * @return the item as it is after the change
	 */
	@Transactional
	public ItemRow change(long actorId, long itemId, Kind kind, int quantity, int seen, String reason, String ipAddress) {
		ItemRow item = repository.lockItem(itemId).filter(found -> !found.deleted()).orElseThrow(ItemWriter::itemGone);
		int before = item.availableQuantity();
		String type;
		AuditAction action;
		int moved;
		long after;
		switch (kind) {
		case STOCK_IN -> {
			type = "RESTOCK";
			action = AuditAction.STOCK_RESTOCK;
			moved = quantity;
			after = (long) before + quantity;
			if (after > Integer.MAX_VALUE) {
				throw new ApiException(ErrorCode.INVALID_QUANTITY, "The stock would pass the most the system can hold").with("field", "quantity");
			}
		}
		case WRITE_OFF -> {
			type = "WRITE_OFF";
			action = AuditAction.STOCK_WRITE_OFF;
			moved = quantity;
			after = (long) before - quantity;
			if (after < 0) {
				throw new ApiException(ErrorCode.OUT_OF_STOCK, "The write-off is larger than the stock");
			}
		}
		case CORRECTION -> {
			if (before != seen) {
				throw new ApiException(ErrorCode.STOCK_CHANGED, "The stock changed since it was read. Reload and count again.");
			}
			type = quantity > before ? "ADJUST_IN" : "ADJUST_OUT";
			action = AuditAction.STOCK_ADJUSTED;
			moved = Math.abs(quantity - before);
			after = quantity;
		}
		default -> throw new IllegalArgumentException(String.valueOf(kind));
		}
		int newQuantity = (int) after;
		repository.setAvailable(itemId, newQuantity);
		long transactionId = repository.insertTransaction(itemId, type, moved, before, newQuantity, reason, actorId);

		Map<String, Object> afterData = new LinkedHashMap<>();
		afterData.put("availableQuantity", newQuantity);
		afterData.put("quantity", moved);
		if (kind == Kind.CORRECTION) {
			afterData.put("direction", "ADJUST_IN".equals(type) ? "IN" : "OUT");
		}
		afterData.put("sku", item.sku());
		afterData.put("name", item.name());
		afterData.put("transactionId", transactionId);
		audit.write(action, actorId, AuditLogWriter.TARGET_ITEM, itemId, Map.of("availableQuantity", before), afterData, ipAddress, reason);
		return new ItemRow(itemId, item.sku(), item.name(), item.imagePath(), newQuantity, false);
	}

}
