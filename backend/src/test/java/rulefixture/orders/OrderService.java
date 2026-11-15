package rulefixture.orders;

/** Allowed: a service uses the repository of its own module. */
public class OrderService {

	private final OrderRepository repository = new OrderRepository();

	public int count() {
		return repository.count();
	}

}
