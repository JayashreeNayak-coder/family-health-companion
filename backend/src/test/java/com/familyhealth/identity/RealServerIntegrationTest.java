package com.familyhealth.identity;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs a second application with real Tomcat on the same database, for what MockMvc can't show:
 * that a session survives a new application context, and how forwarded client addresses are read.
 */
class RealServerIntegrationTest extends IntegrationTestBase {

	private static ConfigurableApplicationContext server;

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@BeforeEach
	void startServer() throws Exception {
		if (server == null) {
			HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
			// Command-line arguments, because they override the required placeholders in application.yml.
			server = new SpringApplicationBuilder(FamilyHealthApplication.class).run(
					"--server.port=0",
					"--spring.datasource.url=" + hikari.getJdbcUrl(),
					"--spring.datasource.username=" + hikari.getUsername(),
					"--spring.datasource.password=" + hikari.getPassword(),
					"--app.documents.storage-dir=" + System.getProperty("java.io.tmpdir") + "/family-health-test-documents");
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
	void sessionWorksInANewApplicationContextAndNotAfterLogout() throws Exception {
		ApiClient client = new ApiClient(mockMvc);
		String email = ApiClient.uniqueEmail();
		client.register(email, ApiClient.PASSWORD);
		client.login(email, ApiClient.PASSWORD);
		String sessionId = client.sessionId();

		HttpResponse<String> me = getMe(sessionId);
		assertThat(me.statusCode()).isEqualTo(200);
		String returned = JsonPath.read(me.body(), "$.email");
		assertThat(returned).isEqualTo(email);

		assertThat(client.post("/api/v1/auth/logout", Map.of()).getStatus()).isEqualTo(204);

		assertThat(getMe(sessionId).statusCode()).isEqualTo(401);
	}

	@Test
	void rateLimitUsesTheForwardedClientAddressFromATrustedProxy() throws Exception {
		String limitedClient = "203.0.113.10";
		for (int i = 0; i < 50; i++) {
			assertThat(login(limitedClient).statusCode()).isEqualTo(401);
		}

		assertThat(login(limitedClient).statusCode()).isEqualTo(429);
		assertThat(login("203.0.113.11").statusCode()).isEqualTo(401);
	}

	/** Boot applies the cookie settings only on an embedded server, so MockMvc can't check this. */
	@Test
	void realServerSendsTheSessionCookieWithItsSecurityAttributes() throws Exception {
		HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).build(),
				HttpResponse.BodyHandlers.ofString());

		String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(cookie).startsWith("SESSION=").contains("HttpOnly", "Secure", "SameSite=Lax");
	}

	private HttpResponse<String> getMe(String sessionId) throws Exception {
		return http.send(HttpRequest.newBuilder(uri("/api/v1/auth/me")).header("Cookie", "SESSION=" + sessionId).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/** A failed login from one client behind the proxy, including the real CSRF exchange. */
	private HttpResponse<String> login(String forwardedFor) throws Exception {
		HttpResponse<String> csrf = http.send(
				HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).header("X-Forwarded-For", forwardedFor).build(),
				HttpResponse.BodyHandlers.ofString());
		String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
		String token = JsonPath.read(csrf.body(), "$.token");
		String body = ApiClient.json(Map.of("email", ApiClient.uniqueEmail(), "password", "wrong-password-value"));
		return http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
			.header("X-Forwarded-For", forwardedFor)
			.header("Cookie", cookie)
			.header("X-CSRF-TOKEN", token)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + server.getEnvironment().getProperty("local.server.port") + path);
	}

}
