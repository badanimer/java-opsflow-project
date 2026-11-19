package com.opsflow.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.opsflow.auth.AdminCreator;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * The demo data against a real server (see Demo data in docs/backend/backend-design.md): the five accounts
 * with their roles, and through the API the items with pictures, the products, the stock changes, the orders
 * with one refused, and a ticket on every screen, each with its log rows; a second run is refused.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SeedDemoTest extends MySqlIntegrationTest {

	private static final String PASSWORD = "demo-pass-1";

	@LocalServerPort
	int port;

	@Autowired
	DemoSeeder seeder;

	@Test
	void theDemoFillsEveryScreenThroughTheApiAndRunsOnce() {
		List<String> said = new ArrayList<>();
		DemoSeeder.Summary made = seeder.seed(PASSWORD, "http://localhost:" + port, said::add);

		assertThat(made).isEqualTo(new DemoSeeder.Summary(6, 5, 3, 1, 6));
		assertThat(said).isNotEmpty();
		assertThat(root().queryForList("""
				SELECT CONCAT(u.username, ':', r.code) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
				ORDER BY u.username""", String.class))
				.containsExactly("adam:ADMIN", "mary:MANAGER", "olive:OPERATOR", "otto:OPERATOR", "vera:VIEWER");
		assertThat(root().queryForObject("SELECT COUNT(*) FROM inventory WHERE image_url IS NOT NULL", Integer.class)).isEqualTo(6);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM products WHERE status = 'LISTED'", Integer.class)).isEqualTo(5);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(3);
		assertThat(root().queryForObject("SELECT COUNT(*) FROM order_attempts WHERE result = 'OUT_OF_STOCK'", Integer.class)).isEqualTo(1);
		assertThat(root().queryForObject("SELECT available_quantity FROM inventory WHERE name = 'Packing tape 48 mm'", Integer.class))
				.as("60 at the start, 40 delivered, 10 ordered").isEqualTo(90);

		Map<String, Object> screens = root().queryForMap("""
				SELECT SUM(status = 'OPEN') AS pool, SUM(status = 'IN_PROGRESS') AS progress, SUM(status = 'RESOLVED') AS resolved,
				       SUM(status = 'CLOSED') AS archived, SUM(status = 'CANCELLED') AS cancelled FROM tickets""");
		assertThat(screens).as("one ticket on each screen, two in progress")
				.allSatisfy((screen, count) -> assertThat(((Number) count).intValue()).isEqualTo("progress".equals(screen) ? 2 : 1));
		assertThat(root().queryForObject("SELECT COUNT(*) FROM tickets WHERE type = 'ORDER_EXCEPTION' AND order_no IS NOT NULL", Integer.class))
				.isEqualTo(1);
		assertThat(root().queryForList("SELECT action FROM operation_logs WHERE category = 'TICKET'", String.class))
				.contains("TICKET_CREATED", "TICKET_TAKEN", "TICKET_ASSIGNED", "TICKET_REASSIGNED", "TICKET_RESOLVED", "TICKET_CLOSED", "TICKET_CANCELLED");

		// The accounts log in with the password typed, and the demo does not run a second time.
		new DemoClient("http://localhost:" + port).login("vera", PASSWORD);
		assertThatThrownBy(() -> seeder.seed(PASSWORD, "http://localhost:" + port, said::add)).isInstanceOf(AdminCreator.Refused.class)
				.hasMessageContaining("adam");
	}

	@Test
	void aWeakPasswordOrABackendThatDoesNotAnswerLeavesNothingBehind() {
		assertThatThrownBy(() -> seeder.seed("short", "http://localhost:" + port, line -> { })).isInstanceOf(AdminCreator.Refused.class);
		assertThatThrownBy(() -> seeder.seed(PASSWORD, "http://localhost:1", line -> { })).isInstanceOf(DemoClient.Failed.class)
				.hasMessageContaining("did not answer");
		assertThat(root().queryForObject("SELECT COUNT(*) FROM users WHERE is_system = FALSE", Integer.class)).isZero();
	}

}
