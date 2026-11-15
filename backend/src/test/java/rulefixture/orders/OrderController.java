package rulefixture.orders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/** Breaks a rule: a controller reads the database itself. */
@RestController
public class OrderController {

	private final JdbcTemplate jdbc;

	public OrderController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Integer count() {
		return jdbc.queryForObject("SELECT 1", Integer.class);
	}

}
