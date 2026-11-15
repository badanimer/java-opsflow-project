package com.opsflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.opsflow.common.error.ApiException;
import com.opsflow.order.OrderService.LineRequest;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;
import com.opsflow.warehouse.ItemService;
import com.opsflow.warehouse.ProductService;
import com.opsflow.warehouse.StockService;

/**
 * The races of orders and stock in Concurrency and duplicate protection and Concurrency by Module
 * (docs/database/database-design.md), repeated against the Java code that the verification script checks
 * with stored procedures (T-17, first pass). Each race runs with the real transactions and locks.
 */
class OrderRaceTest extends OrderIntegrationSupport {

	private static final int ROUNDS = 5;

	@Autowired
	OrderService orders;

	@Autowired
	ProductService products;

	@Autowired
	ItemService items;

	@Autowired
	StockService stock;

	long mary;
	long olive;

	@BeforeEach
	void people() {
		mary = account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
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

	private Change orderOf(long productId, int quantity, String name, String price) {
		return () -> orders.place(actor(olive), null, List.of(new LineRequest(productId, quantity, name, price)), null);
	}

	@Test
	void ordersAtTheSameMomentNeverTakeTheStockBelowZeroAndEachMovementFollowsTheLast() throws Exception {
		long paper = item("mary", "A4 paper", 10);
		long product = product("mary", paper, "A4 paper", "2.00");
		Change[] eight = new Change[8];
		for (int i = 0; i < eight.length; i++) {
			eight[i] = orderOf(product, 2, "A4 paper", "2.00");
		}

		List<String> results = race(eight);

		assertThat(results).filteredOn("OK"::equals).hasSize(5);
		assertThat(results).filteredOn("OUT_OF_STOCK"::equals).hasSize(3);
		assertThat(stock(paper)).containsEntry("available_quantity", 0).containsEntry("sold_quantity", 10);
		List<Map<String, Object>> sales = movements(paper).stream().filter(m -> "SALE".equals(m.get("type"))).toList();
		assertThat(sales).hasSize(5);
		int expected = 10;
		for (Map<String, Object> sale : sales) {
			assertThat(sale.get("available_before")).as("each sale starts where the last ended").isEqualTo(expected);
			expected -= 2;
			assertThat(sale.get("available_after")).isEqualTo(expected);
		}
		assertThat(attempts(product)).extracting(a -> a.get("result")).filteredOn("COMPLETED"::equals).hasSize(5);
		assertThat(attempts(product)).hasSize(8);
	}

	@Test
	void theSameKeySentFourTimesAtOncePlacesOneOrder() throws Exception {
		long paper = item("mary", "A4 paper", 10);
		long product = product("mary", paper, "A4 paper", "2.00");
		long[] ids = new long[4];
		AtomicInteger next = new AtomicInteger();
		Change send = () -> ids[next.getAndIncrement()] = orders
				.place(actor(olive), "double-click", List.of(new LineRequest(product, 1, "A4 paper", "2.00")), null).order().id();

		List<String> results = race(send, send, send, send);

		assertThat(results).containsOnly("OK");
		assertThat(ids).containsOnly(ids[0]);
		assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
		assertThat(available(paper)).isEqualTo(9);
		assertThat(attempts(product)).hasSize(1);
	}

	/** Either the order commits first and the delisting is dated after it, or the delisting commits first and the order is refused. */
	@Test
	void anOrderAndADelistingOfItsProductRunOneAfterTheOther() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String name = "Swing " + round;
			long item = item("mary", name, 5);
			long product = product("mary", item, name, "1.00");

			List<String> results = race(orderOf(product, 1, name, "1.00"), () -> products.delist(actor(mary), product, null));

			assertThat(results.get(1)).as("the delisting, round %d", round).isEqualTo("OK");
			if (results.get(0).equals("OK")) {
				LocalDateTime ordered = root().queryForObject("SELECT o.created_at FROM orders o JOIN order_items i ON i.order_id = o.id "
						+ "WHERE i.product_id = ?", LocalDateTime.class, product);
				LocalDateTime delisted = root().queryForObject("SELECT delisted_at FROM products WHERE id = ?", LocalDateTime.class, product);
				assertThat(delisted).as("round %d", round).isAfterOrEqualTo(ordered);
				assertThat(available(item)).isEqualTo(4);
			}
			else {
				assertThat(results.get(0)).isEqualTo("PRODUCT_DELISTED");
				assertThat(available(item)).isEqualTo(5);
			}
		}
	}

	@Test
	void anOrderPlacedAsItsItemIsDeletedIsEitherCompleteOrRefused() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String name = "Doomed " + round;
			long item = item("mary", name, 5);
			long product = product("mary", item, name, "1.00");

			List<String> results = race(orderOf(product, 2, name, "1.00"), () -> items.delete(actor(mary), item, null));

			assertThat(results.get(1)).as("the deletion, round %d", round).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "PRODUCT_DELISTED");
			assertThat(available(item)).isEqualTo(results.get(0).equals("OK") ? 3 : 5);
			assertThat(count("SELECT COUNT(*) FROM products WHERE id = ? AND status = 'LISTED'", product)).isZero();
		}
	}

	@Test
	void anOrderAndAWriteOffOfTheLastStockCannotBothTakeIt() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String name = "Scarce " + round;
			long item = item("mary", name, 5);
			long product = product("mary", item, name, "1.00");

			List<String> results = race(orderOf(product, 3, name, "1.00"),
					() -> stock.writeOff(actor(olive), item, 3, "damaged", null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "OUT_OF_STOCK");
			assertThat(available(item)).isEqualTo(2);
		}
	}

	/** Either the stock-in commits first and the deletion records the stock it left, or the deletion does and the stock-in finds no item. */
	@Test
	void aStockChangeAndADeletionOfTheItemRunOneAfterTheOther() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long item = item("mary", "Leaving " + round, 5);

			List<String> results = race(() -> stock.stockIn(actor(olive), item, 4, "delivery", null), () -> items.delete(actor(mary), item, null));

			assertThat(results.get(1)).as("the deletion, round %d", round).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "NOT_FOUND");
			int shown = root().queryForObject("SELECT quantity FROM inventory_item_events WHERE inventory_id = ? AND action = 'DELETED'",
					Integer.class, item);
			assertThat(shown).as("the deletion records the stock as it was when it took the row").isEqualTo(available(item))
					.isEqualTo(results.get(0).equals("OK") ? 9 : 5);
		}
	}

	/** A correction counted before an order cannot undo the sale: it is refused, or it comes first and the order takes from it. */
	@Test
	void aCorrectionMadeFromAScreenThatAnOrderMadeStaleIsRefused() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String name = "Counted " + round;
			long item = item("mary", name, 10);
			long product = product("mary", item, name, "1.00");

			List<String> results = race(orderOf(product, 3, name, "1.00"),
					() -> stock.correct(actor(mary), item, 4, 10, "counted", null));

			assertThat(results.get(0)).as("the order, round %d", round).isEqualTo("OK");
			if (results.get(1).equals("OK")) {
				assertThat(available(item)).as("corrected to 4, then 3 sold").isEqualTo(1);
			}
			else {
				assertThat(results.get(1)).isEqualTo("STOCK_CHANGED");
				assertThat(available(item)).isEqualTo(7);
			}
		}
	}

}
