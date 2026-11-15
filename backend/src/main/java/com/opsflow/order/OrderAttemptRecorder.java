package com.opsflow.order;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a refused attempt to place an order in {@code order_attempts} (see Order attempts in
 * docs/database/database-design.md). It runs after the order transaction has rolled back, in a short
 * transaction of its own that takes no lock: the names are read with a plain read. One row is written for
 * each product line with the current name of the product, the result of the whole attempt, and nothing
 * deducted. A line whose product does not exist cannot be recorded, because there is no product to point at.
 *
 * It is a bean of its own, so that the new transaction passes through the proxy.
 */
@Component
public class OrderAttemptRecorder {

	/** A product line as it was sent: the quantity as {@code order_attempts.quantity} records it. */
	record Attempted(long productId, int recordedQuantity) {
	}

	private final OrderRepository repository;
	private final Clock clock;

	OrderAttemptRecorder(OrderRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordRefused(String orderNo, long userId, List<Attempted> lines, String result) {
		Instant at = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		for (Attempted line : lines) {
			repository.productName(line.productId()).ifPresent(name -> repository.insertAttempt(orderNo, userId, line.productId(), name,
					null, line.recordedQuantity(), result, 0, at));
		}
	}

}
