package com.opsflow.order;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Makes the number of an attempt to place an order: {@code ORD-} and a random UUID without hyphens, in
 * lower case (see Order and ticket numbers in docs/database/database-design.md). One number is made for
 * each attempt, before it starts, and a completed attempt keeps it as the number of its order. The unique
 * key {@code uk_orders_order_no} is the last guard: on a clash the caller asks for another one.
 */
@Component
public class OrderNumbers {

	public String next() {
		return "ORD-" + UUID.randomUUID().toString().replace("-", "");
	}

}
