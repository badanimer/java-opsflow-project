package com.opsflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.validation.Quantities;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;

/**
 * The races of the warehouse in Concurrency by Module (docs/database/database-design.md), repeated against
 * the Java code that the verification script checks with stored procedures. Each race runs several
 * rounds with the real transactions and locks.
 */
class WarehouseRaceTest extends WarehouseIntegrationSupport {

	private static final int ROUNDS = 6;

	@Autowired
	ItemService items;

	@Autowired
	ProductService products;

	@Autowired
	ItemWriter itemWriter;

	@Autowired
	WarehouseRepository repository;

	@Autowired
	ImageStore images;

	@Autowired
	Quantities quantities;

	@Autowired
	TransactionRetry retry;

	@Autowired
	OpsflowProperties properties;

	long mary;
	long adam;

	@BeforeEach
	void people() {
		mary = account("mary", "MANAGER");
		adam = account("adam", "ADMIN");
	}

	private interface Change {
		void run();
	}

	/** Runs the changes at the same moment and returns what each did: OK or the code of its refusal. */
	private static List<String> race(Change... changes) {
		AtomicInteger next = new AtomicInteger();
		String[] results = new String[changes.length];
		List<Outcome<Integer>> outcomes = RaceRunner.run(changes.length, () -> {
			int index = next.getAndIncrement();
			try {
				changes[index].run();
				results[index] = "OK";
			}
			catch (ApiException refusal) {
				results[index] = refusal.code().name();
			}
			return index;
		});
		assertThat(outcomes).as("no racer ended with an unexpected exception").allMatch(Outcome::succeeded);
		return List.of(results);
	}

	@Test
	void twoManagersAddAnItemWithTheSameNameAtOnceAndOneItemIsMade() {
		for (int round = 0; round < ROUNDS; round++) {
			String name = "Twin " + round;
			List<String> results = race(() -> items.create(actor(mary), name, null, 5, null),
					() -> items.create(actor(adam), name.toUpperCase(), null, 5, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "ITEM_NAME_TAKEN");
			assertThat(count("SELECT COUNT(*) FROM inventory WHERE name = ?", name)).isEqualTo(1);
			long id = root().queryForObject("SELECT id FROM inventory WHERE name = ?", Long.class, name);
			assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id = ?", id)).as("one starting restock").isEqualTo(1);
			assertThat(count("SELECT COUNT(*) FROM inventory_item_events")).isEqualTo(round + 1);
		}
	}

	@Test
	void twoDeletionsOfTheSameItemAtOnceDeleteItOnce() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = items.create(actor(mary), "Doomed " + round, null, 3, null).id();
			products.list(actor(mary), id, "Doomed " + round, "1.00", null);

			List<String> results = race(() -> items.delete(actor(mary), id, null), () -> items.delete(actor(adam), id, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "NOT_FOUND");
			assertThat(itemEvents(id)).containsExactly("CREATED", "DELETED");
			long product = root().queryForObject("SELECT id FROM products WHERE inventory_id = ?", Long.class, id);
			assertThat(listingEvents(product)).containsExactly("LISTED", "DELISTED");
		}
	}

	@Test
	void twoListingsOfTheSameItemAtOnceMakeOneProductAndOneEvent() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = items.create(actor(mary), "Popular " + round, null, 3, null).id();

			List<String> results = race(() -> products.list(actor(mary), id, "Popular", "1.00", null),
					() -> products.list(actor(adam), id, "Popular", "2.00", null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "ALREADY_LISTED");
			assertThat(count("SELECT COUNT(*) FROM products WHERE inventory_id = ?", id)).isEqualTo(1);
			assertThat(count("SELECT COUNT(*) FROM product_listing_events WHERE inventory_id = ?", id)).isEqualTo(1);
		}
	}

	/** Either the listing commits first and the deletion delists it, or the deletion does and the listing finds no item. */
	@Test
	void aListingAndADeletionOfTheSameItemNeverLeaveADeletedItemOnSale() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = items.create(actor(mary), "Contested " + round, null, 3, null).id();

			List<String> results = race(() -> products.list(actor(mary), id, "Contested", "1.00", null),
					() -> items.delete(actor(adam), id, null));

			assertThat(results.get(1)).as("the deletion, round %d", round).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "NOT_FOUND");
			assertThat(count("SELECT COUNT(*) FROM products WHERE inventory_id = ? AND status = 'LISTED'", id)).isZero();
		}
	}

	@Test
	void aListingAndADelistingOfTheSameProductAtOnceRunOneAfterTheOther() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = items.create(actor(mary), "Swing " + round, null, 3, null).id();
			long product = products.list(actor(mary), id, "Swing", "1.00", null).id();
			products.delist(actor(mary), product, null);

			List<String> results = race(() -> products.list(actor(mary), id, "Swing", "1.50", null),
					() -> products.delist(actor(adam), product, null));

			String status = root().queryForObject("SELECT status FROM products WHERE id = ?", String.class, product);
			if (results.get(1).equals("OK")) {
				assertThat(results.get(0)).as("listed first, then delisted, round %d", round).isEqualTo("OK");
				assertThat(status).isEqualTo("UNLISTED");
			}
			else {
				assertThat(results.get(1)).isEqualTo("ALREADY_DELISTED");
				assertThat(status).isEqualTo("LISTED");
			}
		}
	}

	@Test
	void anEditAndAListingOfTheSameItemRunOneAfterTheOther() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = items.create(actor(mary), "Editable " + round, null, 3, null).id();
			String newName = "Edited " + round;

			List<String> results = race(() -> items.edit(actor(mary), id, newName, null, null),
					() -> products.list(actor(adam), id, "Editable", "1.00", null));

			assertThat(results.get(1)).as("the listing, round %d", round).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "ITEM_LISTED");
		}
	}

	@Test
	void aRepeatedSkuIsReplacedByANewOneAndAddingFailsOnlyWhenEveryAttemptCollides() {
		String taken = items.create(actor(mary), "First", null, 0, null).sku();
		Deque<String> generated = new ArrayDeque<>(List.of(taken, taken, "NEWSKU01"));
		SkuGenerator scripted = new SkuGenerator() {
			@Override
			public String next() {
				return generated.isEmpty() ? taken : generated.pop();
			}
		};
		ItemService withScriptedSkus = new ItemService(itemWriter, repository, images, scripted, quantities, retry, properties);

		assertThat(withScriptedSkus.create(actor(mary), "Second", null, 0, null).sku()).isEqualTo("NEWSKU01");

		ApiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(ApiException.class,
				() -> withScriptedSkus.create(actor(mary), "Third", null, 0, null));
		assertThat(failure.code().name()).isEqualTo("INTERNAL_ERROR");
		assertThat(count("SELECT COUNT(*) FROM inventory WHERE name = 'Third'")).isZero();
	}

	@Test
	void theTimeOfADeletionIsNotEarlierThanTheListingItWaitedFor() {
		long id = items.create(actor(mary), "Timed", null, 3, null).id();
		long product = products.list(actor(mary), id, "Timed", "1.00", null).id();
		items.delete(actor(adam), id, null);
		Object listedAt = root().queryForObject("SELECT listed_at FROM products WHERE id = ?", Object.class, product);
		Object delistedAt = root().queryForObject("SELECT delisted_at FROM products WHERE id = ?", Object.class, product);
		assertThat(((java.time.LocalDateTime) delistedAt)).isAfterOrEqualTo((java.time.LocalDateTime) listedAt);
		assertThat(new BigDecimal("1.00")).isEqualByComparingTo(root().queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class, product));
	}

}
