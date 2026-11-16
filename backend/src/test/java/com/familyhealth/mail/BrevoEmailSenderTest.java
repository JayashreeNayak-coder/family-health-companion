package com.familyhealth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Against a local stand-in for Brevo's API, so the real request shape is checked without sending email. */
class BrevoEmailSenderTest {

	record Received(String method, String path, String apiKey, String contentType, String body) {
	}

	private final JsonMapper json = JsonMapper.builder().build();

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private HttpServer server;

	private BrevoEmailSender sender(int status, String from) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("api-key"), exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			byte[] answer = "{\"messageId\":\"<id@example>\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, answer.length);
			exchange.getResponseBody().write(answer);
			exchange.close();
		});
		server.start();
		return new BrevoEmailSender(json, "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-key", from);
	}

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsOnePlainTextMessageWithTheKeyInAHeader() throws IOException {
		int status = sender(201, "sender@example.com").deliver("to@example.com", "Reset your password", "Open this link");

		assertThat(status).isEqualTo(201);
		assertThat(received).hasSize(1);
		Received request = received.getFirst();
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.path()).isEqualTo("/v3/smtp/email");
		assertThat(request.apiKey()).isEqualTo("test-key");
		assertThat(request.contentType()).isEqualTo("application/json");
		JsonNode body = json.readTree(request.body());
		assertThat(body.path("sender").path("email").asString()).isEqualTo("sender@example.com");
		assertThat(body.path("sender").path("name").asString()).isEqualTo(BrevoEmailSender.SENDER_NAME);
		assertThat(body.path("to").get(0).path("email").asString()).isEqualTo("to@example.com");
		assertThat(body.path("subject").asString()).isEqualTo("Reset your password");
		assertThat(body.path("textContent").asString()).isEqualTo("Open this link");
	}

	@Test
	void refusedRequestIsReportedAndNeverThrown() throws IOException {
		assertThat(sender(401, "sender@example.com").deliver("to@example.com", "S", "T")).isEqualTo(401);
	}

	@Test
	void unreachableServiceIsReportedAndNeverThrown() throws IOException {
		BrevoEmailSender brevo = sender(201, "sender@example.com");
		server.stop(0);
		server = null;

		assertThat(brevo.deliver("to@example.com", "S", "T")).isEqualTo(-1);
	}

	@Test
	void withoutASenderAddressNothingIsSent() throws Exception {
		sender(201, "").send("to@example.com", "S", "T");
		Thread.sleep(200);

		assertThat(received).isEmpty();
	}

}
