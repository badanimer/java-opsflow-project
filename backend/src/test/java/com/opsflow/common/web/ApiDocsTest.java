package com.opsflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * The API documentation in a real server (see API documentation in docs/backend/backend-design.md): the
 * description and Swagger UI open with no login under the base path, describe only the controllers of the
 * application, and say who may call each operation, from the declaration the check itself reads.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiDocsTest extends MySqlIntegrationTest {

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void theDescriptionOpensWithNoLoginAndSaysWhoMayCallEachOperation() throws Exception {
		HttpResponse<String> docs = get("/api/v1/api-docs");

		assertThat(docs.statusCode()).isEqualTo(200);
		String json = docs.body();
		assertThat((String) JsonPath.read(json, "$.info.title")).isEqualTo("OpsFlow API");
		assertThat((String) JsonPath.read(json, "$.paths['/api/v1/auth/login'].post.description")).startsWith("Public: no login needed.");
		Map<String, Object> login = JsonPath.read(json, "$.paths['/api/v1/auth/login'].post");
		assertThat(login).as("a public operation asks for no token").doesNotContainKey("security");
		assertThat((String) JsonPath.read(json, "$.paths['/api/v1/auth/me'].get.description")).startsWith("Any logged-in account.");
		assertThat((String) JsonPath.read(json, "$.paths['/api/v1/orders'].post.description")).startsWith("Needs the permission ORDER_PLACE.");
		assertThat((List<Map<String, Object>>) JsonPath.read(json, "$.paths['/api/v1/orders'].post.security")).containsExactly(Map.of("bearer", List.of()));
		assertThat((String) JsonPath.read(json, "$.components.securitySchemes.bearer.scheme")).isEqualTo("bearer");
		// Only the controllers of the application: not Actuator, not the error page of the container.
		Map<String, Object> paths = JsonPath.read(json, "$.paths");
		assertThat(paths.keySet()).allMatch(path -> path.startsWith("/api/v1/"));
	}

	@Test
	void swaggerUiOpensWithNoLoginAndTheApiStaysClosed() throws Exception {
		HttpResponse<String> page = get("/api/v1/docs");
		assertThat(page.statusCode()).as("the page or a redirect to its files").isIn(200, 302);
		assertThat(get("/api/v1/swagger-ui/index.html").statusCode()).isEqualTo(200);

		// The documentation opens no endpoint: calling one still needs a token.
		assertThat(get("/api/v1/auth/me").statusCode()).isEqualTo(401);
		assertThat(get("/api/v1/swagger-ui/../users").statusCode()).isIn(400, 401, 404);
	}

}
