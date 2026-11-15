package com.opsflow.common.validation;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.springframework.stereotype.Component;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

/**
 * The rule of Valid quantities in docs/database/database-design.md (T-41): a quantity is a whole number in
 * the range of its use, checked before anything is changed and refused with {@code INVALID_QUANTITY}.
 *
 * A request type declares a quantity as {@code Object}, so that the JSON value reaches this class as it
 * was sent. An {@code Integer} field would let the JSON reader turn {@code 1.5} into {@code 1} without a
 * word, and refuse {@code "ten"} as an unreadable body instead of as an invalid quantity. Whether it
 * would take a stock below zero or past the column is checked later, with the row locked.
 */
@Component
public class Quantities {

	private final int maxStockInput;

	public Quantities(OpsflowProperties properties) {
		this.maxStockInput = properties.quantity().maxStockInput();
	}

	/** The starting quantity of a new item: empty means 0, otherwise 0 to the most of one stock input. */
	public int startingQuantity(Object value) {
		if (value == null) {
			return 0;
		}
		return within(value, 0, maxStockInput, "startingQuantity");
	}

	private static int within(Object value, int min, int max, String field) {
		BigInteger whole = wholeNumber(value);
		if (whole == null || whole.compareTo(BigInteger.valueOf(min)) < 0 || whole.compareTo(BigInteger.valueOf(max)) > 0) {
			throw new ApiException(ErrorCode.INVALID_QUANTITY, field + " must be a whole number from " + min + " to " + max)
					.with("field", field);
		}
		return whole.intValueExact();
	}

	/** The value as a whole number, or null when it is text, a fraction, or not a number at all. */
	private static BigInteger wholeNumber(Object value) {
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
			return BigInteger.valueOf(((Number) value).longValue());
		}
		if (value instanceof BigInteger big) {
			return big;
		}
		BigDecimal decimal;
		if (value instanceof BigDecimal d) {
			decimal = d;
		}
		else if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				return null;
			}
			decimal = BigDecimal.valueOf(d);
		}
		else {
			return null;
		}
		try {
			return decimal.stripTrailingZeros().toBigIntegerExact();
		}
		catch (ArithmeticException fraction) {
			return null;
		}
	}

}
