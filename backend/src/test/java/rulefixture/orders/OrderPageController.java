package rulefixture.orders;

import org.springframework.web.bind.annotation.RestController;

/** Allowed: a controller calls the service. */
@RestController
public class OrderPageController {

	private final OrderService service = new OrderService();

	public int count() {
		return service.count();
	}

}
