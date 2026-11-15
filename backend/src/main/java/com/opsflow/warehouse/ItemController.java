package com.opsflow.warehouse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.opsflow.warehouse.WarehouseRepository.ItemRow;
import com.opsflow.warehouse.WarehouseRepository.ListedItem;

import jakarta.servlet.http.HttpServletRequest;

/** The warehouse items of the stock page. See Warehouse Items, Images, and Products in docs/api/api-design.md. */
@RestController
@RequestMapping("/items")
public class ItemController {

	/**
	 * @param imagePath a path that the image upload returned, or null for no image
	 * @param startingQuantity a whole number; empty means 0. Declared as {@code Object} so that the value
	 *        reaches the quantity check as it was sent (see {@link com.opsflow.common.validation.Quantities})
	 */
	public record CreateItemRequest(String name, String imagePath, Object startingQuantity) {
	}

	/** The whole editable state of an item: a null {@code imagePath} removes the image. */
	public record EditItemRequest(String name, String imagePath) {
	}

	/** @param listed whether the product of the item is on sale; an item on sale cannot be edited */
	public record ItemResponse(long id, String sku, String name, String imageUrl, int availableQuantity, boolean listed) {

		static ItemResponse of(ItemRow item, boolean listed) {
			return new ItemResponse(item.id(), item.sku(), item.name(), ImageStore.urlOf(item.imagePath()), item.availableQuantity(), listed);
		}

		static ItemResponse of(ListedItem item) {
			return new ItemResponse(item.id(), item.sku(), item.name(), ImageStore.urlOf(item.imagePath()), item.availableQuantity(),
					item.listed());
		}
	}

	private final ItemService items;
	private final PageQueryFactory pages;

	ItemController(ItemService items, PageQueryFactory pages) {
		this.items = items;
		this.pages = pages;
	}

	/** The items that are not deleted, by name and then id, paged by key. */
	@RequiresPermission(Permission.STOCK_VIEW)
	@GetMapping
	public PageResponse<ItemResponse> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.ASC);
		PageResponse<ListedItem> found = items.list(search, page);
		return new PageResponse<>(found.items().stream().map(ItemResponse::of).toList(), found.nextCursor(), found.prevCursor());
	}

	@RequiresPermission(Permission.ITEM_MANAGE)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ItemResponse create(@RequestBody CreateItemRequest body, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		ItemRow item = items.create(user, body.name(), body.imagePath(), body.startingQuantity(), request.getRemoteAddr());
		return ItemResponse.of(item, false);
	}

	@RequiresPermission(Permission.ITEM_MANAGE)
	@PutMapping("/{id}")
	public ItemResponse edit(@PathVariable long id, @RequestBody EditItemRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return ItemResponse.of(items.edit(user, id, body.name(), body.imagePath(), request.getRemoteAddr()), false);
	}

	/** A soft deletion: the item leaves every list, its product is delisted, and its stock is left as it is. */
	@RequiresPermission(Permission.ITEM_DELETE)
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		items.delete(user, id, request.getRemoteAddr());
	}

}
