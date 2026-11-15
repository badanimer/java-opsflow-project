package com.opsflow.order;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.RequiresPermission;
import com.opsflow.common.web.IdempotencyKey;
import com.opsflow.order.OrderRepository.PlacedLine;
import com.opsflow.order.OrderRepository.PlacedOrder;
import com.opsflow.order.OrderService.LineRequest;

/** Placing an order. See Orders in docs/api/api-design.md. */
@RestController
@RequestMapping("/orders")
public class OrderController {

	/** @param remark optional free text */
	public record PlaceOrderRequest(List<LineRequest> lines, String remark) {
	}

	/** A line of the order as it was frozen: the SKU, the name, and the price it was placed under. */
	public record OrderLineResponse(long productId, String sku, String name, String unitPrice, int quantity, String subtotal) {

		static OrderLineResponse of(PlacedLine line) {
			return new OrderLineResponse(line.productId(), line.sku(), line.name(), line.unitPrice().toPlainString(), line.quantity(),
					line.subtotal().toPlainString());
		}
	}

	/** @param itemCount the number of lines */
	public record OrderResponse(long id, String orderNo, String totalAmount, int itemCount, Instant createdAt, List<OrderLineResponse> lines) {

		static OrderResponse of(PlacedOrder order) {
			return new OrderResponse(order.id(), order.orderNo(), order.totalAmount().toPlainString(), order.itemCount(), order.createdAt(),
					order.lines().stream().map(OrderLineResponse::of).toList());
		}
	}

	private final OrderService orders;

	OrderController(OrderService orders) {
		this.orders = orders;
	}

	/** {@code 201} with the new order, or {@code 200} with the original order of a key used before. */
	@RequiresPermission(Permission.ORDER_PLACE)
	@PostMapping
	public ResponseEntity<OrderResponse> place(@RequestHeader(value = IdempotencyKey.HEADER, required = false) String idempotencyKey,
			@RequestBody PlaceOrderRequest body, @AuthenticationPrincipal CurrentUser user) {
		OrderService.Result result = orders.place(user, idempotencyKey, body.lines(), body.remark());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(OrderResponse.of(result.order()));
	}

}
