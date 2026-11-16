package com.opsflow.warehouse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.ticket.TicketCascades;
import com.opsflow.warehouse.WarehouseRepository.ItemRow;
import com.opsflow.warehouse.WarehouseRepository.ProductRow;

/**
 * The transactions of listing and delisting (T-39; see Listing and delisting and the Products rows of
 * Concurrency by Module in docs/database/database-design.md). Lock order: {@code products}, then
 * {@code inventory}. The time of a listing or a delisting is taken after the locks are held, so no order
 * that committed before it carries a later time.
 */
@Component
public class ProductWriter {

	/** A product as a change leaves it. */
	public record ProductState(long id, long inventoryId, String name, BigDecimal price, String status) {
	}

	private final WarehouseRepository repository;
	private final AuditLogWriter audit;
	private final Clock clock;
	private final TicketCascades tickets;

	ProductWriter(WarehouseRepository repository, AuditLogWriter audit, Clock clock, TicketCascades tickets) {
		this.tickets = tickets;
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Lists an item: the first time it creates its product, later it lists the same product again. The
	 * stock is read under a shared lock, so a write-off cannot change it before this commits.
	 */
	@Transactional
	public ProductState list(long actorId, long inventoryId, String name, BigDecimal price, String ipAddress) {
		ProductRow product = repository.lockProductOfItem(inventoryId).orElse(null);
		ItemRow item = repository.shareItem(inventoryId).filter(found -> !found.deleted()).orElseThrow(ItemWriter::itemGone);
		if (product != null && product.listed()) {
			throw alreadyListed();
		}
		if (item.availableQuantity() <= 0) {
			throw new ApiException(ErrorCode.NO_STOCK, "An item with no stock cannot be listed");
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		long productId;
		if (product == null) {
			try {
				productId = repository.insertProduct(inventoryId, name, price, now, actorId);
			}
			catch (DuplicateKeyException firstListingMeanwhile) {
				throw alreadyListed();
			}
		}
		else {
			productId = product.id();
			if (repository.relist(productId, name, price, now, actorId) == 0) {
				throw alreadyListed();
			}
		}
		repository.insertListingEvent(productId, inventoryId, "LISTED", name, price, actorId);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", "LISTED");
		after.put("inventoryId", inventoryId);
		after.put("name", name);
		after.put("price", price.toPlainString());
		after.put("sku", item.sku());
		audit.write(AuditAction.PRODUCT_LISTED, actorId, AuditLogWriter.TARGET_PRODUCT, productId,
				product == null ? null : Map.of("status", "UNLISTED"), after, ipAddress, null);
		return new ProductState(productId, inventoryId, name, price, "LISTED");
	}

	/** Delists a product. Orders that hold its row shared finish first, and a second delisting is refused. */
	@Transactional
	public ProductState delist(long actorId, long productId, String ipAddress) {
		ProductRow product = repository.lockProduct(productId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "There is no such product"));
		if (!product.listed()) {
			throw new ApiException(ErrorCode.ALREADY_DELISTED, "The product is not on sale");
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		repository.delist(productId, now, actorId);
		repository.insertListingEvent(productId, product.inventoryId(), "DELISTED", product.name(), product.price(), actorId);
		// The tickets linked to the item of the product are cancelled as the SYSTEM identity (T-39).
		TicketCascades.Changed cancelled = tickets.cancelLinkedTo(product.inventoryId(), "PRODUCT_DELISTED:" + productId);
		String sku = repository.findItem(product.inventoryId()).map(ItemRow::sku).orElse(null);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", "UNLISTED");
		after.put("cancelledTicketIds", cancelled.ids());
		after.put("cancelledTicketNos", cancelled.ticketNos());
		after.put("name", product.name());
		after.put("sku", sku);
		audit.write(AuditAction.PRODUCT_DELISTED, actorId, AuditLogWriter.TARGET_PRODUCT, productId, Map.of("status", "LISTED"),
				after, ipAddress, null);
		return new ProductState(productId, product.inventoryId(), product.name(), product.price(), "UNLISTED");
	}

	private static ApiException alreadyListed() {
		return new ApiException(ErrorCode.ALREADY_LISTED, "The item is already on sale");
	}

}
