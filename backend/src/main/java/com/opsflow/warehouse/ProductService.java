package com.opsflow.warehouse;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.warehouse.ItemService.Names;
import com.opsflow.warehouse.ProductWriter.ProductState;
import com.opsflow.warehouse.WarehouseRepository.ListableItem;
import com.opsflow.warehouse.WarehouseRepository.ShopProduct;

/**
 * The product page, the catalog, listing, and delisting (T-39). The values are checked first, with no
 * transaction; then {@link ProductWriter} runs through {@link TransactionRetry}.
 */
@Service
public class ProductService {

	/** A price as it is sent: digits, and at most two decimals. The column holds 10 digits before the point. */
	private static final Pattern PRICE = Pattern.compile("\\d{1,10}(\\.\\d{1,2})?");

	private final ProductWriter writer;
	private final WarehouseRepository repository;
	private final TransactionRetry retry;

	ProductService(ProductWriter writer, WarehouseRepository repository, TransactionRetry retry) {
		this.writer = writer;
		this.repository = repository;
		this.retry = retry;
	}

	/** The product page: the products on sale, by name. */
	public PageResponse<ShopProduct> listOnSale(String search, PageQuery page) {
		return PageResponse.of(repository.listProducts(Names.searchPart(search), page), page,
				product -> new Cursor(product.name(), product.id()));
	}

	/** The catalog: the items that are not deleted and not on sale, by name. */
	public PageResponse<ListableItem> listListable(String search, PageQuery page) {
		return PageResponse.of(repository.listListable(Names.searchPart(search), page), page,
				item -> new Cursor(item.name(), item.inventoryId()));
	}

	public ProductState list(CurrentUser actor, long inventoryId, String name, String price, String ipAddress) {
		String checkedName = Names.itemName("name", name);
		BigDecimal checkedPrice = price(price);
		return retry.run(() -> writer.list(actor.id(), inventoryId, checkedName, checkedPrice, ipAddress));
	}

	public ProductState delist(CurrentUser actor, long productId, String ipAddress) {
		return retry.run(() -> writer.delist(actor.id(), productId, ipAddress));
	}

	/** A price above 0, with at most two decimals, that the column can hold. */
	static BigDecimal price(String price) {
		if (price == null || !PRICE.matcher(price).matches()) {
			throw ApiException.invalid("price", "must be a number above 0 with at most two decimals, such as 12.50");
		}
		BigDecimal value = new BigDecimal(price).setScale(2);
		if (value.signum() <= 0) {
			throw ApiException.invalid("price", "must be above 0");
		}
		return value;
	}

}
