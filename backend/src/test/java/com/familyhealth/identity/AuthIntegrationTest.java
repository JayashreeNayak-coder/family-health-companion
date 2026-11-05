package com.familyhealth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest extends IntegrationTestBase {

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String LOGOUT = "/api/v1/auth/logout";

	private static final String ME = "/api/v1/auth/me";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ApiClient client;

	private String email;

	@BeforeEach
	void setUp() {
		client = new ApiClient(mockMvc);
		email = ApiClient.uniqueEmail();
	}

	@Test
	void registerLoginReadMeAndLogout() throws Exception {
		MockHttpServletResponse registered = client.register(email, ApiClient.PASSWORD);
		assertThat(registered.getStatus()).isEqualTo(201);
		assertThat(string(registered, "$.email")).isEqualTo(email);
		assertThat(registered.getContentAsString()).doesNotContain("password");

		// Registering does not sign in.
		assertThat(client.get(ME).getStatus()).isEqualTo(401);

		assertThat(client.login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(200);

		MockHttpServletResponse me = client.get(ME);
		assertThat(me.getStatus()).isEqualTo(200);
		assertThat(string(me, "$.email")).isEqualTo(email);
		assertThat(string(me, "$.displayName")).isEqualTo("Synthetic User");

		assertThat(client.post(LOGOUT, Map.of()).getStatus()).isEqualTo(204);
		assertThat(client.get(ME).getStatus()).isEqualTo(401);
	}

	@Test
	void emailIsCaseInsensitiveAndStoredLowercase() throws Exception {
		String mixedCase = "User-" + UUID.randomUUID() + "@Example.TEST";

		assertThat(client.register(mixedCase, ApiClient.PASSWORD).getStatus()).isEqualTo(201);

		assertThat(client.login(mixedCase.toUpperCase(), ApiClient.PASSWORD).getStatus()).isEqualTo(200);
		assertThat(string(client.get(ME), "$.email")).isEqualTo(mixedCase.toLowerCase());
	}

	@Test
	void duplicateEmailReturnsConflictInStandardFormat() throws Exception {
		client.register(email, ApiClient.PASSWORD);

		MockHttpServletResponse duplicate = client.register(email.toUpperCase(), ApiClient.PASSWORD);

		assertThat(duplicate.getStatus()).isEqualTo(409);
		assertThat(duplicate.getContentType()).isEqualTo("application/problem+json");
		assertThat(string(duplicate, "$.code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
		assertThat(string(duplicate, "$.type")).isEqualTo("about:blank");
	}

	static List<Map<String, String>> invalidRegistrations() {
		String valid = ApiClient.PASSWORD;
		return List.of(
				Map.of("email", "not-an-email", "password", valid, "displayName", "Name", "field", "email"),
				Map.of("email", " ", "password", valid, "displayName", "Name", "field", "email"),
				Map.of("email", "a@example.test", "password", "x".repeat(11), "displayName", "Name", "field", "password"),
				Map.of("email", "a@example.test", "password", "x".repeat(73), "displayName", "Name", "field", "password"),
				// 40 characters, but 80 bytes: BCrypt could not use all of it.
				Map.of("email", "a@example.test", "password", "é".repeat(40), "displayName", "Name", "field", "password"),
				Map.of("email", "a@example.test", "password", valid, "displayName", " ", "field", "displayName"),
				Map.of("email", "a@example.test", "password", valid, "displayName", "x".repeat(101), "field", "displayName"));
	}

	@ParameterizedTest
	@MethodSource("invalidRegistrations")
	void invalidRegistrationReturnsStandardValidationError(Map<String, String> input) throws Exception {
		MockHttpServletResponse response = client.register(input.get("email"), input.get("password"), input.get("displayName"));

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		assertThat(string(response, "$.code")).isEqualTo("VALIDATION_FAILED");
		assertThat(string(response, "$.title")).isEqualTo("Validation failed");
		assertThat(string(response, "$.errors[0].field")).isEqualTo(input.get("field"));
		assertThat(response.getContentAsString()).doesNotContain(input.get("password"));
	}

	@Test
	void missingPasswordIsRejected() throws Exception {
		MockHttpServletResponse response = client.postRaw(REGISTER,
				ApiClient.json(Map.of("email", email, "displayName", "Name")));

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(string(response, "$.errors[0].field")).isEqualTo("password");
	}

	@Test
	void passwordAtEachLengthLimitIsAccepted() throws Exception {
		assertThat(client.register(ApiClient.uniqueEmail(), "x".repeat(12)).getStatus()).isEqualTo(201);

		String longest = "x".repeat(72);
		assertThat(client.register(email, longest).getStatus()).isEqualTo(201);
		assertThat(client.login(email, longest).getStatus()).isEqualTo(200);
	}

	@Test
	void malformedJsonReturnsStandardError() throws Exception {
		MockHttpServletResponse response = client.postRaw(REGISTER, "{not json");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(string(response, "$.code")).isEqualTo("BAD_REQUEST");
		assertThat(response.getContentAsString()).doesNotContain("jackson", "Exception");
	}

	@Test
	void wrongPasswordUnknownEmailAndDisabledAccountReturnTheSameResponse() throws Exception {
		client.register(email, ApiClient.PASSWORD);
		String disabled = ApiClient.uniqueEmail();
		client.register(disabled, ApiClient.PASSWORD);
		jdbc.update("UPDATE user_account SET status = 'DISABLED' WHERE email = ?", disabled);

		MockHttpServletResponse wrongPassword = client.login(email, "wrong-password-value");
		MockHttpServletResponse unknown = client.login(ApiClient.uniqueEmail(), ApiClient.PASSWORD);
		MockHttpServletResponse disabledAccount = client.login(disabled, ApiClient.PASSWORD);

		assertThat(wrongPassword.getStatus()).isEqualTo(401);
		assertThat(string(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknown.getStatus()).isEqualTo(401);
		assertThat(unknown.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
		assertThat(disabledAccount.getStatus()).isEqualTo(401);
		assertThat(disabledAccount.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
	}

	@Test
	void accountIsRateLimitedEvenForTheCorrectPassword() throws Exception {
		client.register(email, ApiClient.PASSWORD);
		for (int i = 0; i < 10; i++) {
			assertThat(client.login(email, "wrong-password-value").getStatus()).isEqualTo(401);
		}

		MockHttpServletResponse limited = new ApiClient(mockMvc, "198.51.100.77").login(email, ApiClient.PASSWORD);

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(string(limited, "$.code")).isEqualTo("RATE_LIMITED");
		assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
	}

	@Test
	void unknownEmailIsRateLimitedLikeAKnownOne() throws Exception {
		String unknown = ApiClient.uniqueEmail();
		for (int i = 0; i < 10; i++) {
			assertThat(client.login(unknown, "wrong-password-value").getStatus()).isEqualTo(401);
		}

		assertThat(client.login(unknown, "wrong-password-value").getStatus()).isEqualTo(429);
	}

	@Test
	void addressIsRateLimitedAcrossAccounts() throws Exception {
		ApiClient sameAddress = new ApiClient(mockMvc, "198.51.100.200");
		for (int i = 0; i < 50; i++) {
			assertThat(sameAddress.login(ApiClient.uniqueEmail(), "wrong-password-value").getStatus()).isEqualTo(401);
		}

		assertThat(sameAddress.login(ApiClient.uniqueEmail(), "wrong-password-value").getStatus()).isEqualTo(429);
		assertThat(new ApiClient(mockMvc, "198.51.100.201").login(ApiClient.uniqueEmail(), "wrong-password-value")
			.getStatus()).isEqualTo(401);
	}

	@Test
	void writeWithoutCsrfTokenIsRejectedAndChangesNothing() throws Exception {
		String body = ApiClient.json(Map.of("email", email, "password", ApiClient.PASSWORD, "displayName", "Name"));

		MockHttpServletResponse response = client.postWithToken(REGISTER, body, null);

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(string(response, "$.code")).isEqualTo("CSRF_REJECTED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE email = ?", Integer.class, email))
			.isZero();
	}

	@Test
	void writeWithWrongCsrfTokenIsRejected() throws Exception {
		client.csrfToken();
		String body = ApiClient.json(Map.of("email", email, "password", ApiClient.PASSWORD, "displayName", "Name"));

		assertThat(client.postWithToken(REGISTER, body, "not-the-token").getStatus()).isEqualTo(403);
	}

	@Test
	void signedOutRequestsReturn401InStandardFormat() throws Exception {
		for (String path : List.of(ME, "/api/v1/anything-else")) {
			MockHttpServletResponse response = client.get(path);

			assertThat(response.getStatus()).isEqualTo(401);
			assertThat(response.getContentType()).isEqualTo("application/problem+json");
			assertThat(string(response, "$.code")).isEqualTo("UNAUTHENTICATED");
		}
	}

	@Test
	void signedOutLogoutIsRejected() throws Exception {
		assertThat(client.post(LOGOUT, Map.of()).getStatus()).isEqualTo(401);
	}

	@Test
	void signedOutReadCreatesNoSession() throws Exception {
		MockHttpServletResponse response = client.get(ME);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getCookie("SESSION")).isNull();
	}

	@Test
	void loginRotatesSessionIdAndCsrfToken() throws Exception {
		client.register(email, ApiClient.PASSWORD);
		String tokenBeforeLogin = client.csrfToken();
		String sessionBeforeLogin = client.sessionId();

		assertThat(client.login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(200);

		assertThat(client.sessionId()).isNotNull().isNotEqualTo(sessionBeforeLogin);
		assertThat(client.postWithToken(LOGOUT, "{}", tokenBeforeLogin).getStatus()).isEqualTo(403);
	}

	@Test
	void sessionStoresNoEmailAndIdleTimeoutIsThirtyMinutes() throws Exception {
		signIn();
		UUID accountId = accountId();

		Map<String, Object> session = jdbc.queryForMap(
				"SELECT principal_name, max_inactive_interval FROM spring_session WHERE principal_name = ?",
				accountId.toString());

		assertThat(session.get("max_inactive_interval")).isEqualTo(1800);
	}

	@Test
	void sessionEndsAfterIdleTimeout() throws Exception {
		signIn();
		long thirtyOneMinutesAgo = System.currentTimeMillis() - 31 * 60 * 1000L;
		jdbc.update("UPDATE spring_session SET last_access_time = ? WHERE principal_name = ?", thirtyOneMinutesAgo,
				accountId().toString());

		assertThat(client.get(ME).getStatus()).isEqualTo(401);
	}

	@Test
	void sessionEndsAfterAbsoluteTimeoutEvenWhenActive() throws Exception {
		signIn();
		assertThat(client.get(ME).getStatus()).isEqualTo(200);
		long thirteenHoursAgo = System.currentTimeMillis() - 13 * 60 * 60 * 1000L;
		jdbc.update("UPDATE spring_session SET creation_time = ? WHERE principal_name = ?", thirteenHoursAgo,
				accountId().toString());

		assertThat(client.get(ME).getStatus()).isEqualTo(401);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				accountId().toString())).isZero();
	}

	@Test
	void disablingAnAccountEndsItsSession() throws Exception {
		signIn();
		assertThat(client.get(ME).getStatus()).isEqualTo(200);

		jdbc.update("UPDATE user_account SET status = 'DISABLED' WHERE email = ?", email);

		assertThat(client.get(ME).getStatus()).isEqualTo(401);
	}

	@Test
	void passwordsAreStoredHashedWithBcrypt() throws Exception {
		client.register(email, ApiClient.PASSWORD);

		String stored = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE email = ?", String.class,
				email);

		assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(ApiClient.PASSWORD);
	}

	@Test
	void auditEventsAreWrittenForEachAction() throws Exception {
		signIn();
		UUID accountId = accountId();
		client.login(email, "wrong-password-value");
		client.postWithToken(LOGOUT, "{}", null);
		client.post(LOGOUT, Map.of());

		assertThat(jdbc.queryForList(
				"SELECT action || ':' || outcome FROM audit_event WHERE actor_account_id = ? ORDER BY occurred_at",
				String.class, accountId))
			.containsExactly("ACCOUNT_REGISTERED:SUCCESS", "LOGIN:SUCCESS", "ACCESS_DENIED:DENIED", "LOGOUT:SUCCESS");
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_event WHERE action = 'LOGIN' AND outcome = 'FAILURE' AND actor_account_id IS NULL AND target_id = ?",
				Integer.class, accountId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT metadata ->> 'reason' FROM audit_event WHERE action = 'ACCESS_DENIED' AND actor_account_id = ?",
				String.class, accountId)).isEqualTo("CSRF");
	}

	@Test
	void failedLoginForUnknownEmailIsAuditedWithoutTheEmail() throws Exception {
		String unknown = ApiClient.uniqueEmail();
		Integer before = auditCountWithFailedLoginAndNoTarget();

		client.login(unknown, "wrong-password-value");

		assertThat(auditCountWithFailedLoginAndNoTarget()).isGreaterThan(before);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE audit_event::text LIKE ?", Integer.class,
				"%" + unknown + "%")).isZero();
	}

	@Test
	void auditEventsHoldNoPasswordsTokensOrEmails() throws Exception {
		signIn();
		String token = client.csrfToken();
		client.postWithToken(LOGOUT, "{}", token);

		for (String secret : List.of(ApiClient.PASSWORD, token, email)) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE audit_event::text LIKE ?",
					Integer.class, "%" + secret + "%")).isZero();
		}
	}

	@Test
	void logsHoldNoPasswordsOrEmails(CapturedOutput output) throws Exception {
		signIn();
		client.login(email, "wrong-password-value");
		client.post(LOGOUT, Map.of());

		assertThat(output.getAll()).doesNotContain(ApiClient.PASSWORD, "wrong-password-value", email);
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		MockHttpServletResponse response = client.get(ME);

		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
	}

	private void signIn() throws Exception {
		client.register(email, ApiClient.PASSWORD);
		assertThat(client.login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(200);
	}

	private UUID accountId() {
		return jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", UUID.class, email);
	}

	private Integer auditCountWithFailedLoginAndNoTarget() {
		return jdbc.queryForObject(
				"SELECT count(*) FROM audit_event WHERE action = 'LOGIN' AND outcome = 'FAILURE' AND target_id IS NULL",
				Integer.class);
	}

	private static String string(MockHttpServletResponse response, String path) throws Exception {
		return JsonPath.read(response.getContentAsString(), path);
	}

}
