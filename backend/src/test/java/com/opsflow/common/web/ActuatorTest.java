package com.opsflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.opsflow.support.MySqlIntegrationTest;

/**
 * Actuator in a real server with the two ports of the deployment (see Deployment in
 * docs/backend/backend-design.md): the health check and the counters answer on the management port, with
 * no login, and not on the port of the API, which the reverse proxy passes on.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ActuatorTest extends MySqlIntegrationTest {

	@LocalServerPort
	int apiPort;

	@LocalManagementPort
	int managementPort;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void theHealthCheckSeesTheDatabaseRedisAndRabbitMqOnTheManagementPort() throws Exception {
		HttpResponse<String> health = get(managementPort, "/actuator/health");

		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).contains("\"status\":\"UP\"");
		assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
	}

	@Test
	void theCountersOfRetriesAndBusyAnswersAreReadOnTheManagementPort() throws Exception {
		assertThat(get(managementPort, "/actuator/metrics/opsflow.retry.retries").statusCode()).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/metrics/opsflow.retry.busy").body()).contains("opsflow.retry.busy");
	}

	@Test
	void thePortOfTheApiDoesNotAnswerForActuator() throws Exception {
		assertThat(managementPort).isNotEqualTo(apiPort);
		assertThat(get(apiPort, "/actuator/health").statusCode()).isIn(401, 403, 404);
		assertThat(get(apiPort, "/actuator/metrics").statusCode()).isIn(401, 403, 404);
	}

}
