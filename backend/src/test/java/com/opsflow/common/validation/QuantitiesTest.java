package com.opsflow.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;

/** The rule of Valid quantities (T-41), value by value, for the starting quantity of an item. */
class QuantitiesTest {

	private final Quantities quantities = new Quantities(new OpsflowProperties(null, null, null, null, null, null, null,
			new OpsflowProperties.Quantity(999_999, 9999), null));

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
		for (Object value : new Object[] { 1.5d, new BigDecimal("0.1"), "10", "ten", true, -1, 1_000_000, Long.MAX_VALUE,
				new BigInteger("99999999999999999999"), Double.NaN, Double.POSITIVE_INFINITY }) {
			assertThatThrownBy(() -> quantities.startingQuantity(value)).as(String.valueOf(value)).isInstanceOf(ApiException.class)
					.satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("INVALID_QUANTITY"));
		}
	}

}
