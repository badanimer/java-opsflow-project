package rulefixture.common;

import rulefixture.orders.OrderService;

/** Breaks a rule: the shared code depends on a module. */
public class SharedShortcut {

	public int count() {
		return new OrderService().count();
	}

}
