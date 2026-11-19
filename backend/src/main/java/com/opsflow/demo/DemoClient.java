package com.opsflow.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The HTTP client of the demo data: it calls the API of a running backend as a person would, with the token
 * of that person, so every row of the demo goes through the same permission checks, locks, and log rows as
 * a change made on a page.
 */
final class DemoClient {

	/** The API answered with another status than the step expected. */
	static final class Failed extends RuntimeException {

		private static final long serialVersionUID = 1L;

		Failed(String message) {
			super(message);
		}
	}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final JsonMapper json = JsonMapper.builder().build();
	private final String base;

	/** @param server the address of the backend, such as {@code http://backend:8080} */
	DemoClient(String server) {
		this.base = server.replaceAll("/+$", "") + "/api/v1";
	}

	/** Logs in and returns the token. */
	String login(String username, String password) {
		return send(null, "POST", "/auth/login", Map.of("username", username, "password", password), 200).get("token").asString();
	}

	JsonNode get(String token, String path) {
		return send(token, "GET", path, null, 200);
	}

	/** Sends a change that must succeed, with any status of the 2xx range. */
	JsonNode post(String token, String path, Object body) {
		return send(token, "POST", path, body, 0);
	}

	/** Sends a change that must be answered with this status, such as a refused order. */
	JsonNode postExpecting(String token, String path, Object body, int status) {
		return send(token, "POST", path, body, status);
	}

	/** Uploads a PNG as the picture of an item and returns the path the item is made with. */
	String uploadImage(String token, String fileName, byte[] png) {
		String boundary = "demo" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
				+ "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.writeBytes(png);
		body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/item-images")).timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
		return answer(request, "POST /item-images", 0).get("path").asString();
	}

	private JsonNode send(String token, String method, String path, Object body, int status) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json");
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		}
		return answer(request.build(), method + " " + path, status);
	}

	private JsonNode answer(HttpRequest request, String step, int status) {
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException failed) {
			throw new Failed("The backend at " + base + " did not answer " + step + ". Is it running? (" + failed.getClass().getSimpleName() + ")");
		}
		catch (InterruptedException stopped) {
			Thread.currentThread().interrupt();
			throw new Failed("Stopped while waiting for " + step);
		}
		boolean expected = status == 0 ? response.statusCode() / 100 == 2 : response.statusCode() == status;
		JsonNode answer = response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
		if (!expected) {
			JsonNode code = answer.get("code");
			throw new Failed(step + " was answered " + response.statusCode() + (code == null ? "" : " " + code.asString())
					+ (answer.has("detail") ? ": " + answer.get("detail").asString() : ""));
		}
		return answer;
	}

}
