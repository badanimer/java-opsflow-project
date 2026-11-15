package com.opsflow.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.validation.Limits;
import com.opsflow.common.validation.Quantities;
import com.opsflow.common.web.IdempotencyKey;
import com.opsflow.order.OrderAttemptRecorder.Attempted;
import com.opsflow.order.OrderRepository.PlacedOrder;
import com.opsflow.order.OrderWriter.KeyUsed;
import com.opsflow.order.OrderWriter.Line;
import com.opsflow.order.OrderWriter.NumberTaken;

/**
 * Placing an order (T-10, T-12, T-41). No transaction of its own:
 * <ol>
 * <li>the shape of the request is checked: a malformed one is refused with {@code VALIDATION_FAILED} and
 * is not an attempt;</li>
 * <li>a key the user already used returns the original order, and nothing is written;</li>
 * <li>the attempt gets its number, and a quantity that is not valid refuses it with
 * {@code INVALID_QUANTITY};</li>
 * <li>{@link OrderWriter} runs through {@link TransactionRetry}.</li>
 * </ol>
 * A refused attempt is recorded by {@link OrderAttemptRecorder} after its transaction has rolled back, and
 * every refusal that is recorded carries its {@code orderNo}. The record of a refusal is best effort: if it
 * fails, the refusal is still answered and the failure goes to the log.
 */
@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	/** A price as it is sent: digits, and at most two decimals. */
	private static final Pattern PRICE = Pattern.compile("\\d{1,10}(\\.\\d{1,2})?");

	/** The refusals of the order transaction that are recorded as attempts. */
	private static final Set<ErrorCode> RECORDED = Set.of(ErrorCode.PRODUCT_DELISTED, ErrorCode.PRODUCT_CHANGED, ErrorCode.OUT_OF_STOCK,
			ErrorCode.BUSY);

	/** A product line as it was sent. The quantity is the JSON value, checked by {@link Quantities}. */
	public record LineRequest(Long productId, Object quantity, String name, String price) {
	}

	/** @param created false when the key was used before and this is the original order */
	public record Result(PlacedOrder order, boolean created) {
	}

	/** A line whose shape is checked; the quantity is not checked yet. */
	private record Shaped(long productId, Object quantity, String name, BigDecimal price) {
	}

	private final OrderWriter writer;
	private final OrderAttemptRecorder recorder;
	private final OrderRepository repository;
	private final OrderNumbers numbers;
	private final Quantities quantities;
	private final TransactionRetry retry;
	private final int maxLines;
	private final int numberAttempts;

	OrderService(OrderWriter writer, OrderAttemptRecorder recorder, OrderRepository repository, OrderNumbers numbers,
			Quantities quantities, TransactionRetry retry, OpsflowProperties properties) {
		this.writer = writer;
		this.recorder = recorder;
		this.repository = repository;
		this.numbers = numbers;
		this.quantities = quantities;
		this.retry = retry;
		this.maxLines = properties.order().maxLines();
		this.numberAttempts = properties.order().numberAttempts();
	}

	public Result place(CurrentUser actor, String idempotencyHeader, List<LineRequest> lines, String remark) {
		String key = IdempotencyKey.parse(idempotencyHeader).orElse(null);
		List<Shaped> shaped = shape(lines);
		String checkedRemark = remark(remark);
		if (key != null) {
			Optional<PlacedOrder> original = repository.findByKey(actor.id(), key);
			if (original.isPresent()) {
				return new Result(original.get(), false);
			}
		}

		String orderNo = numbers.next();
		List<Line> checked = new ArrayList<>(shaped.size());
		for (Shaped line : shaped) {
			int quantity;
			try {
				quantity = quantities.orderLine(line.quantity());
			}
			catch (ApiException invalid) {
				record(orderNo, actor.id(), shaped, ErrorCode.INVALID_QUANTITY);
				throw invalid.with("orderNo", orderNo).with("productId", line.productId());
			}
			checked.add(new Line(line.productId(), quantity, line.name(), line.price()));
		}

		for (int attempt = 1; attempt <= numberAttempts; attempt++) {
			String number = orderNo;
			try {
				return new Result(retry.run(() -> writer.place(actor.id(), number, key, checkedRemark, checked)), true);
			}
			catch (KeyUsed used) {
				// The same key committed an order a moment ago: that order is the answer.
				return new Result(repository.findByKey(actor.id(), key)
						.orElseThrow(() -> new IllegalStateException("The order of a used key is missing")), false);
			}
			catch (NumberTaken clash) {
				orderNo = numbers.next();
			}
			catch (ApiException refusal) {
				if (RECORDED.contains(refusal.code())) {
					record(number, actor.id(), shaped, refusal.code());
					throw refusal.with("orderNo", number);
				}
				throw refusal;
			}
		}
		throw new ApiException(ErrorCode.INTERNAL_ERROR, "No free order number was found in " + numberAttempts + " attempts");
	}

	private void record(String orderNo, long userId, List<Shaped> lines, ErrorCode result) {
		List<Attempted> attempted = lines.stream().map(line -> new Attempted(line.productId(), Quantities.asRecorded(line.quantity())))
				.toList();
		try {
			recorder.recordRefused(orderNo, userId, attempted, result.name());
		}
		catch (RuntimeException failure) {
			log.warn("The refused attempt {} ({}) could not be recorded: {}", orderNo, result, failure.toString());
		}
	}

	/** The lines in ascending product id: one to the most lines, each product once, each with the name and price seen. */
	private List<Shaped> shape(List<LineRequest> lines) {
		if (lines == null || lines.isEmpty() || lines.size() > maxLines) {
			throw ApiException.invalid("lines", "must hold 1 to " + maxLines + " lines");
		}
		List<Shaped> shaped = new ArrayList<>(lines.size());
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			LineRequest line = lines.get(i);
			String field = "lines[" + i + "]";
			if (line == null || line.productId() == null) {
				throw ApiException.invalid(field + ".productId", "is required");
			}
			if (!seen.add(line.productId())) {
				throw ApiException.invalid(field + ".productId", "names a product that another line names");
			}
			if (line.name() == null || line.name().isEmpty() || line.name().length() > Limits.ITEM_NAME) {
				throw ApiException.invalid(field + ".name", "must be the name shown, 1 to " + Limits.ITEM_NAME + " characters");
			}
			if (line.price() == null || !PRICE.matcher(line.price()).matches()) {
				throw ApiException.invalid(field + ".price", "must be the price shown, such as 12.50");
			}
			shaped.add(new Shaped(line.productId(), line.quantity(), line.name(), new BigDecimal(line.price())));
		}
		shaped.sort(Comparator.comparingLong(Shaped::productId));
		return shaped;
	}

	/** An optional remark, trimmed; blank is none. */
	private static String remark(String remark) {
		if (remark == null || remark.isBlank()) {
			return null;
		}
		String trimmed = remark.strip();
		if (trimmed.length() > Limits.ORDER_REMARK) {
			throw ApiException.invalid("remark", "must be at most " + Limits.ORDER_REMARK + " characters");
		}
		return trimmed;
	}

}
