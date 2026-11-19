package com.opsflow.demo;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.opsflow.auth.DemoAccounts;
import com.opsflow.demo.DemoImages.Kind;

import tools.jackson.databind.JsonNode;

/**
 * The demo data (see Demo data in docs/backend/backend-design.md): five accounts, and then, through the API of
 * a running backend as those people, warehouse items with pictures, products, stock changes, orders (one of
 * them refused), and tickets on every screen. Every row goes through the same checks and leaves the same log
 * rows as a change made on a page, so the logs and the audit page have something to show.
 *
 * It is made for a new deployment: it refuses to run when a demo account exists, and stops at an item whose
 * name is taken.
 */
@Component
public class DemoSeeder {

	/** What the demo made, for the message at the end. */
	public record Summary(int items, int products, int orders, int refusedOrders, int tickets) {
	}

	private record Item(String name, Kind kind, Color color, int quantity, String price) {
	}

	/** The items of the warehouse: the last has no price, so it stays off the product page. */
	private static final List<Item> ITEMS = List.of(
			new Item("Copy paper A4, 500 sheets", Kind.PAPER, new Color(66, 133, 244), 40, "6.50"),
			new Item("Ballpoint pens, box of 12", Kind.PENS, new Color(52, 73, 94), 25, "4.20"),
			new Item("Packing tape 48 mm", Kind.TAPE, new Color(214, 160, 66), 60, "2.80"),
			new Item("Cardboard box, medium", Kind.BOX, new Color(176, 124, 79), 30, "1.90"),
			new Item("Printer toner, black", Kind.TONER, new Color(40, 40, 46), 4, "79.00"),
			new Item("Label printer rolls", Kind.LABELS, new Color(46, 160, 120), 12, null));

	private final DemoAccounts accounts;

	DemoSeeder(DemoAccounts accounts) {
		this.accounts = accounts;
	}

	/**
	 * Makes the demo data.
	 *
	 * @param server the address of the running backend, such as {@code http://backend:8080}
	 * @param say where the progress is written, one line for each step
	 */
	public Summary seed(String password, String server, Consumer<String> say) {
		DemoClient api = new DemoClient(server);
		// The backend must answer before the accounts are made, so a wrong address leaves nothing behind.
		api.get(null, "/api-docs");
		say.accept("Making the accounts adam (administrator), mary (manager), olive and otto (operators), and vera (viewer)...");
		Map<String, Long> ids = accounts.create(password);
		String mary = api.login("mary", password);
		String olive = api.login("olive", password);
		String otto = api.login("otto", password);

		say.accept("Adding the warehouse items with their pictures, and putting five of them on sale...");
		Map<String, Long> items = new LinkedHashMap<>();
		Map<String, JsonNode> products = new LinkedHashMap<>();
		for (Item item : ITEMS) {
			String image = api.uploadImage(mary, item.kind().name().toLowerCase() + ".png", DemoImages.png(item.kind(), item.color()));
			JsonNode made = api.post(mary, "/items", Map.of("name", item.name(), "imagePath", image, "startingQuantity", item.quantity()));
			items.put(item.name(), made.get("id").asLong());
			if (item.price() != null) {
				products.put(item.name(), api.post(mary, "/products",
						Map.of("inventoryId", made.get("id").asLong(), "name", item.name(), "price", item.price())));
			}
		}
		long paper = items.get("Copy paper A4, 500 sheets");
		long pens = items.get("Ballpoint pens, box of 12");
		long tape = items.get("Packing tape 48 mm");
		long box = items.get("Cardboard box, medium");
		long toner = items.get("Printer toner, black");

		say.accept("Changing the stock: a delivery, a write-off, and a count...");
		api.post(olive, "/items/" + tape + "/stock-in", Map.of("quantity", 40, "reason", "Delivery from the supplier"));
		api.post(olive, "/items/" + box + "/write-off", Map.of("quantity", 3, "reason", "Crushed in delivery"));
		api.post(mary, "/items/" + pens + "/correction", Map.of("quantity", 23, "expectedQuantity", 25, "reason", "Counted on the shelf"));

		say.accept("Placing orders, one of them more than the stock...");
		order(api, olive, products, Map.of("Copy paper A4, 500 sheets", 5, "Ballpoint pens, box of 12", 2));
		order(api, otto, products, Map.of("Packing tape 48 mm", 10));
		order(api, olive, products, Map.of("Cardboard box, medium", 6));
		JsonNode refused = api.postExpecting(olive, "/orders", Map.of("lines", lines(products, Map.of("Printer toner, black", 10))), 409);
		String refusedOrderNo = refused.get("orderNo").asString();

		say.accept("Opening tickets and moving them through each screen...");
		long oliveId = ids.get("olive");
		long ottoId = ids.get("otto");
		// The pool: a ticket waits to be taken.
		ticket(api, otto, "INVENTORY_EXCEPTION", "Packing tape count looks short", "The shelf holds fewer rolls than the stock page says.",
				List.of(tape), null);
		// In progress: one given by a manager, and one taken and then handed over.
		long reorder = ticket(api, olive, "OTHER", "Reorder black toner", "Only four cartridges are left. Ask the supplier for a delivery date.",
				List.of(), null);
		api.post(mary, "/tickets/" + reorder + "/assign", Map.of("assigneeId", oliveId, "reason", "Olive knows the supplier"));
		long shortOrder = ticket(api, olive, "ORDER_EXCEPTION", "Order for 10 toners could not be placed",
				"The customer asked for ten cartridges; the stock had four. Agree on a smaller order or a later date.", List.of(toner), refusedOrderNo);
		api.post(olive, "/tickets/" + shortOrder + "/take", null);
		api.post(mary, "/tickets/" + shortOrder + "/reassign",
				Map.of("assigneeId", ottoId, "expectedAssigneeId", oliveId, "reason", "Olive is on leave this week"));
		// Resolved: taken and resolved by the same person.
		long crushed = ticket(api, otto, "INVENTORY_EXCEPTION", "Three boxes crushed in delivery", "Three medium boxes arrived crushed.",
				List.of(box), null);
		api.post(otto, "/tickets/" + crushed + "/take", null);
		api.post(otto, "/tickets/" + crushed + "/resolve", Map.of("resolution", "Written off and reported to the carrier."));
		// Archived: resolved, then archived by a manager.
		long paperCount = ticket(api, olive, "INVENTORY_EXCEPTION", "Check the count of copy paper", "Confirm the count after the last order.",
				List.of(paper), null);
		api.post(olive, "/tickets/" + paperCount + "/take", null);
		api.post(olive, "/tickets/" + paperCount + "/resolve", Map.of("resolution", "Counted 35 packs, the same as the stock page."));
		api.post(mary, "/tickets/" + paperCount + "/archive", null);
		// Cancelled: opened twice.
		long twice = ticket(api, olive, "INVENTORY_EXCEPTION", "Packing tape count looks short", "Opened by mistake.", List.of(tape), null);
		api.post(mary, "/tickets/" + twice + "/cancel", Map.of("reason", "Opened twice: the same problem waits in the pool"));

		return new Summary(ITEMS.size(), products.size(), 3, 1, 6);
	}

	private static void order(DemoClient api, String token, Map<String, JsonNode> products, Map<String, Integer> quantities) {
		api.post(token, "/orders", Map.of("lines", lines(products, quantities)));
	}

	/** The lines of an order, with the name and the price the person saw, as the page sends them. */
	private static List<Map<String, Object>> lines(Map<String, JsonNode> products, Map<String, Integer> quantities) {
		List<Map<String, Object>> lines = new ArrayList<>();
		quantities.forEach((name, quantity) -> {
			JsonNode product = products.get(name);
			lines.add(Map.of("productId", product.get("id").asLong(), "quantity", quantity, "name", product.get("name").asString(), "price",
					product.get("price").asString()));
		});
		return lines;
	}

	private static long ticket(DemoClient api, String token, String type, String title, String description, List<Long> items, String orderNo) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", type);
		body.put("title", title);
		body.put("description", description);
		body.put("inventoryIds", items);
		body.put("orderNo", orderNo);
		return api.post(token, "/tickets", body).get("id").asLong();
	}

}
