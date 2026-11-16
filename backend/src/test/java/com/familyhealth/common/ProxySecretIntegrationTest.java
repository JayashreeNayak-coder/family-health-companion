package com.familyhealth.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.FamilyHealthApplication;
import com.familyhealth.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A real server with PROXY_SECRET set, as on the hosted demo (D-033): only the proxy gets in, and
 * rate limits use the visitor's address that the proxy sends.
 */
class ProxySecretIntegrationTest extends IntegrationTestBase {

	private static final String SECRET = "test-proxy-secret-0123456789abcdef0123456789";

	private static ConfigurableApplicationContext server;

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private DataSource dataSource;

	@BeforeEach
	void startServer() throws Exception {
		if (server == null) {
			HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
			server = new SpringApplicationBuilder(FamilyHealthApplication.class).run("--server.port=0",
					"--spring.datasource.url=" + hikari.getJdbcUrl(), "--spring.datasource.username=" + hikari.getUsername(),
					"--spring.datasource.password=" + hikari.getPassword(),
					"--app.documents.storage-dir=" + System.getProperty("java.io.tmpdir") + "/family-health-test-documents",
					"--app.documents.s3.bucket=", "--app.mail.brevo.api-key=", "--app.ai.worker.enabled=false",
					"--app.proxy.secret=" + SECRET);
		}
	}

	@AfterAll
	static void stopServer() {
		if (server != null) {
			server.close();
			server = null;
		}
	}

	@Test
	void requestWithoutTheSecretIsRefusedBeforeAnySessionIsMade() throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("PROXY_REQUIRED");
		assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
	}

	@Test
	void requestWithAWrongSecretIsRefused() throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).header(ProxySecretFilter.SECRET_HEADER, SECRET + "x").build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
	}

	@Test
	void requestWithTheSecretIsServed() throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).header(ProxySecretFilter.SECRET_HEADER, SECRET).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
	}

	/** The host's own health probe can't send the secret. The answer is only UP or DOWN. */
	@Test
	void healthCheckNeedsNoSecretButOtherActuatorPathsDo() throws Exception {
		HttpResponse<String> health = http.send(HttpRequest.newBuilder(uri("/actuator/health")).build(),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> other = http.send(HttpRequest.newBuilder(uri("/actuator/health/db")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).contains("UP");
		assertThat(other.statusCode()).isEqualTo(403);
	}

	/** Through the proxy every request comes from Cloudflare, so only the forwarded visitor address can tell them apart. */
	@Test
	void rateLimitUsesTheVisitorAddressFromTheProxyAndIgnoresForwardedFor() throws Exception {
		String limited = "198.51.100.20";
		for (int i = 0; i < 50; i++) {
			// A changing X-Forwarded-For must not give a visitor a fresh allowance.
			assertThat(login(limited, "203.0.113." + i).statusCode()).isEqualTo(401);
		}

		assertThat(login(limited, "203.0.113.200").statusCode()).isEqualTo(429);
		assertThat(login("198.51.100.21", "203.0.113.200").statusCode()).isEqualTo(401);
	}

	/** A failed login from one visitor, with the real CSRF exchange, as the proxy would send it. */
	private HttpResponse<String> login(String visitor, String forwardedFor) throws Exception {
		HttpResponse<String> csrf = http.send(proxied("/api/v1/auth/csrf", visitor, forwardedFor).build(),
				HttpResponse.BodyHandlers.ofString());
		String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
		String token = JsonPath.read(csrf.body(), "$.token");
		String body = ApiClient.json(Map.of("email", ApiClient.uniqueEmail(), "password", "wrong-password-value"));
		return http.send(proxied("/api/v1/auth/login", visitor, forwardedFor).header("Cookie", cookie)
			.header("X-CSRF-TOKEN", token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder proxied(String path, String visitor, String forwardedFor) {
		return HttpRequest.newBuilder(uri(path))
			.header(ProxySecretFilter.SECRET_HEADER, SECRET)
			.header(ProxySecretFilter.CLIENT_ADDRESS_HEADER, visitor)
			.header("X-Forwarded-For", forwardedFor);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + server.getEnvironment().getProperty("local.server.port") + path);
	}

}
