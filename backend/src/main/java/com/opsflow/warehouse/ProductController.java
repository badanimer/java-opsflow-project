package com.opsflow.warehouse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.paging.SortOrder;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.RequiresPermission;
import com.opsflow.warehouse.ProductWriter.ProductState;
import com.opsflow.warehouse.WarehouseRepository.ListableItem;
import com.opsflow.warehouse.WarehouseRepository.ShopProduct;

import jakarta.servlet.http.HttpServletRequest;

/** The product page, the catalog, listing, and delisting. See Warehouse Items, Images, and Products in docs/api/api-design.md. */
@RestController
@RequestMapping("/products")
public class ProductController {

	/** @param price a string with at most two decimals, such as {@code "12.50"} */
	public record ListRequest(long inventoryId, String name, String price) {
	}

	/** @param price with two decimals, as a string, so that no rounding happens on the way */
	public record ProductResponse(long id, long inventoryId, String name, String price, String status) {

		static ProductResponse of(ProductState product) {
			return new ProductResponse(product.id(), product.inventoryId(), product.name(), product.price().toPlainString(),
					product.status());
		}
	}

	/** A row of the product page: nothing else is shown, the stock included. */
	public record ShopProductResponse(long id, String name, String price, String imageUrl) {
	}

	public record ListableItemResponse(long inventoryId, String sku, String name, String imageUrl, int availableQuantity) {
	}

	private final ProductService products;
	private final PageQueryFactory pages;

	ProductController(ProductService products, PageQueryFactory pages) {
		this.products = products;
		this.pages = pages;
	}

	@RequiresPermission(Permission.PRODUCT_VIEW)
	@GetMapping
	public PageResponse<ShopProductResponse> onSale(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.createProductPage(limit, cursor, direction, order);
		PageResponse<ShopProduct> found = products.listOnSale(search, page);
		return new PageResponse<>(found.items().stream()
				.map(p -> new ShopProductResponse(p.id(), p.name(), p.price().toPlainString(), ImageStore.urlOf(p.imagePath())))
				.toList(), found.nextCursor(), found.prevCursor());
	}

	/** The catalog of List: the items that are not deleted and not on sale. */
	@RequiresPermission(Permission.PRODUCT_LIST)
	@GetMapping("/listable")
	public PageResponse<ListableItemResponse> listable(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.ASC);
		PageResponse<ListableItem> found = products.listListable(search, page);
		return new PageResponse<>(found.items().stream()
				.map(i -> new ListableItemResponse(i.inventoryId(), i.sku(), i.name(), ImageStore.urlOf(i.imagePath()), i.availableQuantity()))
				.toList(), found.nextCursor(), found.prevCursor());
	}

	/** Lists an item: its first listing creates the product, a later one lists the same product again. */
	@RequiresPermission(Permission.PRODUCT_LIST)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse list(@RequestBody ListRequest body, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		return ProductResponse.of(products.list(user, body.inventoryId(), body.name(), body.price(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.PRODUCT_DELIST)
	@PostMapping("/{id}/delist")
	public ProductResponse delist(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		return ProductResponse.of(products.delist(user, id, request.getRemoteAddr()));
	}

}
