package com.opsflow.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.order.OrderRepository.PlacedLine;
import com.opsflow.order.OrderRepository.PlacedOrder;
import com.opsflow.order.OrderRepository.ProductRow;

/**
 * The transaction that places an order (T-10; see Create order and Concurrency and duplicate protection in
 * docs/database/database-design.md). The order is complete and final when it commits, and a refusal rolls
 * everything back, so a refused order leaves nothing but the record that its caller writes afterwards.
 *
 * The steps, in the lock order {@code products}, {@code orders}, {@code inventory}:
 * <ol>
 * <li>read every product with {@code FOR SHARE}, in ascending id, and refuse a product that is not on sale
 * ({@code PRODUCT_DELISTED}) or whose name or price is not the one the user saw ({@code PRODUCT_CHANGED});</li>
 * <li>insert the order and its items, the SKU read with a plain read;</li>
 * <li>lock every {@code inventory} row with {@code FOR UPDATE}, in ascending id, and refuse the whole order
 * when any item is short ({@code OUT_OF_STOCK});</li>
 * <li>deduct the stock, write one {@code SALE} per item with the snapshots read under the lock, and one
 * {@code COMPLETED} attempt per line.</li>
 * </ol>
 * The time of the order is taken after the product locks are held, and the time of its movements after the
 * stock locks are held, so a delisting or a stock change that committed first is never dated later.
 */
@Component
public class OrderWriter {

	/** A product line, checked: the quantity is valid, and the name and price are the ones the user saw. */
	record Line(long productId, int quantity, String name, BigDecimal price) {
	}

	/** The idempotency key is used by an order of this user that has committed: the caller returns that order. */
	static final class KeyUsed extends RuntimeException {

		private static final long serialVersionUID = 1L;

		KeyUsed() {
			super("The idempotency key was used before", null, false, false);
		}
	}

	/** The order number was issued before: the caller makes another and tries again. */
	static final class NumberTaken extends RuntimeException {

		private static final long serialVersionUID = 1L;

		NumberTaken() {
			super("The order number was issued before", null, false, false);
		}
	}

	private final OrderRepository repository;
	private final Clock clock;

	OrderWriter(OrderRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * @param lines the lines in ascending product id, one per product
	 * @param idempotencyKey the key of the request, or null
	 */
	@Transactional
	public PlacedOrder place(long userId, String orderNo, String idempotencyKey, String remark, List<Line> lines) {
		// 1. The products, shared, in ascending id.
		List<ProductRow> products = new ArrayList<>(lines.size());
		for (Line line : lines) {
			products.add(repository.shareProduct(line.productId()).orElseThrow(
					() -> new ApiException(ErrorCode.NOT_FOUND, "A product of the order does not exist").with("productId", line.productId())));
		}
		for (ProductRow product : products) {
			if (!product.listed()) {
				throw new ApiException(ErrorCode.PRODUCT_DELISTED, product.name() + " is no longer on sale");
			}
		}
		for (int i = 0; i < lines.size(); i++) {
			ProductRow product = products.get(i);
			Line line = lines.get(i);
			if (!product.name().equals(line.name()) || product.price().compareTo(line.price()) != 0) {
				throw new ApiException(ErrorCode.PRODUCT_CHANGED, "The name or the price of " + product.name() + " has changed");
			}
		}

		// 2. The order and its items.
		Instant placedAt = now();
		List<PlacedLine> placed = new ArrayList<>(lines.size());
		BigDecimal total = BigDecimal.ZERO;
		for (int i = 0; i < lines.size(); i++) {
			ProductRow product = products.get(i);
			int quantity = lines.get(i).quantity();
			BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(quantity));
			total = total.add(subtotal);
			placed.add(new PlacedLine(product.id(), repository.skuOf(product.inventoryId()), product.name(), product.price(), quantity,
					subtotal));
		}
		long orderId;
		try {
			orderId = repository.insertOrder(orderNo, userId, total, lines.size(), idempotencyKey, remark, placedAt);
		}
		catch (DuplicateKeyException duplicate) {
			String message = String.valueOf(duplicate.getMessage());
			if (message.contains("uk_orders_user_idempotency")) {
				throw new KeyUsed();
			}
			if (message.contains("uk_orders_order_no")) {
				throw new NumberTaken();
			}
			throw duplicate;
		}
		for (PlacedLine line : placed) {
			repository.insertOrderItem(orderId, line);
		}

		// 3. The stock, exclusive, in ascending inventory id; every row is locked before any is checked.
		List<Integer> byItem = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			byItem.add(i);
		}
		byItem.sort(Comparator.comparingLong(i -> products.get(i).inventoryId()));
		int[] available = new int[lines.size()];
		for (int i : byItem) {
			long inventoryId = products.get(i).inventoryId();
			available[i] = repository.lockStock(inventoryId)
					.orElseThrow(() -> new IllegalStateException("The item " + inventoryId + " of a listed product is missing"));
		}
		for (int i : byItem) {
			if (available[i] < lines.get(i).quantity()) {
				throw new ApiException(ErrorCode.OUT_OF_STOCK, "Not enough stock of " + products.get(i).name());
			}
		}

		// 4. The deduction, the movements, and the record of the attempt.
		Instant soldAt = now();
		for (int i : byItem) {
			int quantity = lines.get(i).quantity();
			long inventoryId = products.get(i).inventoryId();
			repository.deduct(inventoryId, quantity);
			repository.insertSale(inventoryId, orderId, userId, quantity, available[i], available[i] - quantity, soldAt);
		}
		for (PlacedLine line : placed) {
			repository.insertAttempt(orderNo, userId, line.productId(), line.name(), orderId, line.quantity(), "COMPLETED", line.quantity(),
					soldAt);
		}
		return new PlacedOrder(orderId, orderNo, total, lines.size(), placedAt, placed);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

}
