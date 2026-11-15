package rulefixture.tickets;

import rulefixture.orders.OrderRepository;

/** Breaks a rule: a module uses the repository of another module. */
public class TicketShortcut {

	private final OrderRepository orders = new OrderRepository();

	public int count() {
		return orders.count();
	}

}
