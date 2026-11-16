package com.familyhealth.mail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Brevo's transactional email API over HTTPS, for hosts that block SMTP ports, such as Render's
 * free plan (DECISIONS.md, D-033). Plain HTTP with the JDK client, so no SDK is needed. The key
 * travels in a header, and neither addresses nor message text are logged.
 */
class BrevoEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

	static final String SENDER_NAME = "Family Health Companion";

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

	private final JsonMapper json;

	private final String baseUrl;

	private final String apiKey;

	private final String from;

	BrevoEmailSender(JsonMapper json, String baseUrl, String apiKey, String from) {
		this.json = json;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey;
		this.from = from;
	}

	/** Logs at startup whether email can go out. Checks the key once in the background, sending nothing. */
	void reportStatus() {
		if (from == null || !from.contains("@")) {
			log.warn("Email sending through Brevo is off: set MAIL_FROM to the sender address verified in Brevo");
			return;
		}
		log.info("Email sending is on, through Brevo. Checking the API key in the background");
		background.execute(() -> {
			try {
				int status = call("GET", "/v3/account", null);
				if (status == 200) {
					log.info("Brevo API key works");
				}
				else {
					log.warn("Brevo refused the API key check (status {}), so no email will be sent. Check BREVO_API_KEY",
							status);
				}
			}
			catch (IOException ex) {
				log.warn("Could not reach Brevo: {}", ex.getClass().getSimpleName());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
	}

	@Override
	public void send(String to, String subject, String text) {
		if (from == null || !from.contains("@")) {
			log.warn("Email sender is not configured, so an email was not sent");
			return;
		}
		background.execute(() -> deliver(to, subject, text));
	}

	/** Sends one message and returns the HTTP status, or -1 when Brevo could not be reached. Never throws. */
	int deliver(String to, String subject, String text) {
		ObjectNode body = json.createObjectNode();
		body.putObject("sender").put("name", SENDER_NAME).put("email", from);
		body.putArray("to").addObject().put("email", to);
		body.put("subject", subject);
		body.put("textContent", text);
		try {
			int status = call("POST", "/v3/smtp/email", json.writeValueAsString(body));
			if (status < 200 || status >= 300) {
				log.warn("Email could not be sent: Brevo answered with status {}{}", status,
						status == 401 ? ". Check BREVO_API_KEY" : "");
			}
			return status;
		}
		catch (IOException ex) {
			log.warn("Email could not be sent: {}", ex.getClass().getSimpleName());
			return -1;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}

	private int call(String method, String path, String jsonBody) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(TIMEOUT)
			.header("api-key", apiKey)
			.header("Accept", "application/json");
		if (jsonBody == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", "application/json")
				.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
		}
		// The response body is discarded: it can echo addresses.
		return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
