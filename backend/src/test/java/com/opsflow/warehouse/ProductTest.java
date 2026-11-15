package com.opsflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The product page, the catalog, listing, and delisting (T-39), through HTTP against the MySQL container.
 * The rules are in Listing and delisting and The product page in docs/database/database-design.md.
 */
class ProductTest extends WarehouseIntegrationSupport {

	@BeforeEach
	void people() {
		account("mary", "MANAGER");
		account("olivia", "OPERATOR");
		account("victor", "VIEWER");
	}

	@Test
	void theFirstListingCreatesTheProductWithAnEventAndALogRow() throws Exception {
		long item = item("mary", "A4 paper", 10);

		listItem("mary", item, "A4 paper, 500 sheets", "4.50").andExpect(status().isCreated())
				.andExpect(jsonPath("$.inventoryId").value(item))
				.andExpect(jsonPath("$.name").value("A4 paper, 500 sheets"))
				.andExpect(jsonPath("$.price").value("4.50"))
				.andExpect(jsonPath("$.status").value("LISTED"));
		long product = root().queryForObject("SELECT id FROM products WHERE inventory_id = ?", Long.class, item);

		assertThat(productRow(product).get("price")).isEqualTo(new BigDecimal("4.50"));
		assertThat(listingEvents(product)).containsExactly("LISTED");
		assertThat(logActions("PRODUCT", product)).containsExactly("PRODUCT_LISTED");
		assertThat(logData("PRODUCT_LISTED", product, "after_data")).contains("\"price\": \"4.50\"").contains("\"sku\"");
		assertThat(logData("PRODUCT_LISTED", product, "before_data")).isNull();
	}

	@Test
	void listingAgainAfterADelistingKeepsTheProductIdAndTakesTheNewNameAndPrice() throws Exception {
		long item = item("mary", "Pen", 3);
		long product = listed("mary", item, "Pen", "1.00");
		delist("mary", product).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNLISTED"));

		listItem("mary", item, "Blue pen", "1.20").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(product));

		assertThat(productRow(product).get("name")).isEqualTo("Blue pen");
		assertThat(productRow(product).get("delisted_at")).isNull();
		assertThat(listingEvents(product)).containsExactly("LISTED", "DELISTED", "LISTED");
		assertThat(logData("PRODUCT_LISTED", product, "before_data")).contains("UNLISTED");
	}

	@Test
	void anItemWithNoStockIsNotListedAndNothingIsWritten() throws Exception {
		long item = item("mary", "Empty box", 0);
		listItem("mary", item, "Empty box", "2.00").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_STOCK"));
		assertThat(count("SELECT COUNT(*) FROM products")).isZero();
		assertThat(count("SELECT COUNT(*) FROM product_listing_events")).isZero();
	}

	@Test
	void anItemOnSaleADeletedItemAndAProductNotOnSaleAreRefused() throws Exception {
		long item = item("mary", "Clip", 5);
		long product = listed("mary", item, "Clip", "0.10");
		listItem("mary", item, "Clip", "0.20").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_LISTED"));

		delist("mary", product).andExpect(status().isOk());
		delist("mary", product).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_DELISTED"));
		delist("mary", 999999).andExpect(status().isNotFound());

		long gone = item("mary", "Gone", 5);
		deleteItem("mary", gone);
		listItem("mary", gone, "Gone", "1.00").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		listItem("mary", 999999, "Nothing", "1.00").andExpect(status().isNotFound());
	}

	@Test
	void aPriceMustBeAboveZeroWithAtMostTwoDecimalsAndFitTheColumn() throws Exception {
		long item = item("mary", "Cup", 5);
		for (String price : new String[] { "0", "0.00", "-1", "1.234", "abc", "", "12345678901", "1e3" }) {
			listItem("mary", item, "Cup", price).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[0].field").value("price"));
		}
		listItem("mary", item, "Cup", "9999999999.99").andExpect(status().isCreated()).andExpect(jsonPath("$.price").value("9999999999.99"));
	}

	@Test
	void theProductPageShowsOnlyProductsOnSaleByNameWithASearchAndNoStock() throws Exception {
		long a = item("mary", "Zebra pen", 1);
		long b = item("mary", "Apple box", 1);
		long c = item("mary", "Mug", 1);
		listed("mary", a, "Zebra pen", "2.00");
		listed("mary", b, "Apple box", "3.00");
		long mug = listed("mary", c, "Mug", "4.00");
		delist("mary", mug);

		getAs("victor", "/products").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].name", contains("Apple box", "Zebra pen")))
				.andExpect(jsonPath("$.items[0].price").value("3.00"))
				.andExpect(jsonPath("$.items[0].availableQuantity").doesNotExist());
		getAs("olivia", "/products?search=PEN").andExpect(jsonPath("$.items[*].name", contains("Zebra pen")));
	}

	@Test
	void theCatalogOffersTheItemsThatAreNotDeletedAndNotOnSale() throws Exception {
		long onSale = item("mary", "Bag", 1);
		item("mary", "Cord", 0);
		long gone = item("mary", "Dish", 1);
		long relistable = item("mary", "Apron", 2);
		listed("mary", onSale, "Bag", "1.00");
		long apron = listed("mary", relistable, "Apron", "1.00");
		delist("mary", apron);
		deleteItem("mary", gone);

		getAs("mary", "/products/listable").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].name", contains("Apron", "Cord")))
				.andExpect(jsonPath("$.items[0].availableQuantity").value(2));
	}

	@Test
	void onlyAManagerOrAnAdministratorListsDelistsOrOpensTheCatalog() throws Exception {
		long item = item("mary", "Fan", 1);
		long product = listed("mary", item, "Fan", "9.00");
		getAs("olivia", "/products/listable").andExpect(status().isForbidden());
		listItem("olivia", item, "Fan", "9.00").andExpect(status().isForbidden());
		delist("victor", product).andExpect(status().isForbidden());
		getAs("victor", "/products").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
		assertThat(productRow(product).get("status")).isEqualTo("LISTED");
	}

}
