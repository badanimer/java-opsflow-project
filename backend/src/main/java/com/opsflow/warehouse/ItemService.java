package com.opsflow.warehouse;

import org.springframework.stereotype.Service;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.validation.Limits;
import com.opsflow.common.validation.Quantities;
import com.opsflow.warehouse.ItemWriter.SkuTaken;
import com.opsflow.warehouse.WarehouseRepository.ItemRow;
import com.opsflow.warehouse.WarehouseRepository.ListedItem;

/**
 * Warehouse items (T-39, T-41, T-42). The values are checked first, with no transaction: the name, the
 * image path, and the starting quantity. Then {@link ItemWriter} runs through {@link TransactionRetry}.
 * The SKU is generated here, before the transaction, and generated again when the key refuses it.
 */
@Service
public class ItemService {

	private final ItemWriter writer;
	private final WarehouseRepository repository;
	private final ImageStore images;
	private final SkuGenerator skus;
	private final Quantities quantities;
	private final TransactionRetry retry;
	private final int skuAttempts;

	ItemService(ItemWriter writer, WarehouseRepository repository, ImageStore images, SkuGenerator skus, Quantities quantities,
			TransactionRetry retry, OpsflowProperties properties) {
		this.writer = writer;
		this.repository = repository;
		this.images = images;
		this.skus = skus;
		this.quantities = quantities;
		this.retry = retry;
		this.skuAttempts = properties.warehouse().skuAttempts();
	}

	/** The stock page: the items that are not deleted, by name, paged by key. */
	public PageResponse<ListedItem> list(String search, PageQuery page) {
		return PageResponse.of(repository.listItems(Names.searchPart(search), page), page, item -> new Cursor(item.name(), item.id()));
	}

	public ItemRow create(CurrentUser actor, String name, String imagePath, Object startingQuantity, String ipAddress) {
		String checkedName = Names.itemName("name", name);
		checkImage(imagePath);
		int quantity = quantities.startingQuantity(startingQuantity);
		for (int attempt = 1; attempt <= skuAttempts; attempt++) {
			String sku = skus.next();
			try {
				return retry.run(() -> writer.create(actor.id(), sku, checkedName, imagePath, quantity, ipAddress));
			}
			catch (SkuTaken collision) {
				// Another item has this SKU. The transaction rolled back; try a new one.
			}
		}
		throw new ApiException(ErrorCode.INTERNAL_ERROR, "No free SKU was found in " + skuAttempts + " attempts");
	}

	public ItemRow edit(CurrentUser actor, long itemId, String name, String imagePath, String ipAddress) {
		String checkedName = Names.itemName("name", name);
		checkImage(imagePath);
		return retry.run(() -> writer.edit(actor.id(), itemId, checkedName, imagePath, ipAddress));
	}

	public void delete(CurrentUser actor, long itemId, String ipAddress) {
		retry.run(() -> writer.delete(actor.id(), itemId, ipAddress));
	}

	/** An image path must be one that the upload returned, and its file must be there. */
	private void checkImage(String imagePath) {
		if (imagePath != null && !images.exists(imagePath)) {
			throw ApiException.invalid("imagePath", "must be a path returned by the image upload");
		}
	}

	/** The rules of names and of the search, shared by items and products. */
	static final class Names {

		private Names() {
		}

		/** A name, trimmed: 1 to 200 characters. */
		static String itemName(String field, String name) {
			String trimmed = name == null ? "" : name.strip();
			if (trimmed.isEmpty() || trimmed.length() > Limits.ITEM_NAME) {
				throw ApiException.invalid(field, "must be 1 to " + Limits.ITEM_NAME + " characters");
			}
			return trimmed;
		}

		/** A part of a name to search for, or null for no search. */
		static String searchPart(String search) {
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

}
