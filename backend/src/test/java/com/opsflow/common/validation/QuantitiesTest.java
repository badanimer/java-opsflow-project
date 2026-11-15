package com.opsflow.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;

/**
 * The rule of Valid quantities (T-41), value by value: the starting quantity of an item, a stock-in or a
 * write-off, the counted quantity of a correction and the quantity it saw, and an order line.
 */
class QuantitiesTest {

	private static final Object[] NEVER_VALID = { 1.5d, new BigDecimal("0.1"), "10", "ten", true, -1, Long.MAX_VALUE,
			new BigInteger("99999999999999999999"), Double.NaN, Double.POSITIVE_INFINITY };

	private final Quantities quantities = new Quantities(new OpsflowProperties(null, null, null, null, null, null, null,
			new OpsflowProperties.Quantity(999_999, 9999), null, null, null));

	@Test
	void anEmptyStartingQuantityIsZero() {
		assertThat(quantities.startingQuantity(null)).isZero();
	}

	@Test
	void wholeNumbersFromZeroToTheMostOfOneStockInputAreAccepted() {
		assertThat(quantities.startingQuantity(0)).isZero();
		assertThat(quantities.startingQuantity(999_999)).isEqualTo(999_999);
		assertThat(quantities.startingQuantity(25L)).isEqualTo(25);
		assertThat(quantities.startingQuantity(new BigDecimal("3.00"))).isEqualTo(3);
		assertThat(quantities.startingQuantity(3.0d)).isEqualTo(3);
	}

	@Test
	void fractionsTextNegativesAndTooLargeValuesAreRefused() {
		refusesEach(quantities::startingQuantity, NEVER_VALID);
		refusesEach(quantities::startingQuantity, 1_000_000);
	}

	@Test
	void aStockInOrAWriteOffIsFromOneToTheMostOfOneStockInputAndRequired() {
		assertThat(quantities.stockChange(1)).isEqualTo(1);
		assertThat(quantities.stockChange(999_999)).isEqualTo(999_999);
		refusesEach(quantities::stockChange, NEVER_VALID);
		refusesEach(quantities::stockChange, null, 0, 1_000_000);
	}

	@Test
	void aCorrectionCountsFromZeroAndTheQuantitySeenIsAnyStock() {
		assertThat(quantities.countedQuantity(0)).isZero();
		assertThat(quantities.countedQuantity(999_999)).isEqualTo(999_999);
		refusesEach(quantities::countedQuantity, NEVER_VALID);
		refusesEach(quantities::countedQuantity, null, 1_000_000);
		assertThat(quantities.seenQuantity(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
		refusesEach(quantities::seenQuantity, null, -1, 2.5d, "3");
	}

	@Test
	void anOrderLineIsFromOneToTheMostOfOneOrderLine() {
		assertThat(quantities.orderLine(1)).isEqualTo(1);
		assertThat(quantities.orderLine(9999)).isEqualTo(9999);
		refusesEach(quantities::orderLine, NEVER_VALID);
		refusesEach(quantities::orderLine, null, 0, 10_000);
	}

	@Test
	void anAttemptRecordsTheQuantityAsSentWhenTheColumnHoldsItAndZeroOtherwise() {
		assertThat(Quantities.asRecorded(-3)).isEqualTo(-3);
		assertThat(Quantities.asRecorded(10_000)).isEqualTo(10_000);
		assertThat(Quantities.asRecorded(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
		assertThat(Quantities.asRecorded(Integer.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE);
		assertThat(Quantities.asRecorded(1L + Integer.MAX_VALUE)).isZero();
		assertThat(Quantities.asRecorded(1.5d)).isZero();
		assertThat(Quantities.asRecorded("ten")).isZero();
		assertThat(Quantities.asRecorded(null)).isZero();
	}

	private static void refusesEach(Function<Object, Integer> check, Object... values) {
		for (Object value : values) {
			assertThatThrownBy(() -> check.apply(value)).as(String.valueOf(value)).isInstanceOf(ApiException.class)
					.satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("INVALID_QUANTITY"));
		}
	}

}
