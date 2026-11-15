package rulefixture.tickets;

import rulefixture.common.SharedText;
import rulefixture.orders.OrderService;

/** Allowed: a module calls the service of another module, and the shared code. */
public class TicketService {

	private final OrderService orders = new OrderService();

	public String describe() {
		return SharedText.trim(" " + orders.count());
	}

}
