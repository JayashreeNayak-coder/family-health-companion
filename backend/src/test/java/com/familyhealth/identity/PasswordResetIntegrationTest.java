package com.familyhealth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.FakeEmailSender;
import com.familyhealth.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class PasswordResetIntegrationTest extends IntegrationTestBase {

	private static final String REQUEST = "/api/v1/auth/password-reset/request";

	private static final String CONFIRM = "/api/v1/auth/password-reset/confirm";

	private static final Pattern LINK = Pattern.compile("/reset-password#token=([A-Za-z0-9_-]+)");

	private static final String NEW_PASSWORD = "a-new-synthetic-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeEmailSender mail;

	private String email;

	private ApiClient signedIn;

	private ApiClient visitor;

	@BeforeEach
	void setUp() throws Exception {
		mail.reset();
		email = ApiClient.uniqueEmail();
		signedIn = new ApiClient(mockMvc);
		signedIn.signUp(email, "Reset Synthetic");
		visitor = new ApiClient(mockMvc);
	}

	private String requestLink() throws Exception {
		assertThat(visitor.post(REQUEST, Map.of("email", email.toUpperCase())).getStatus()).isEqualTo(202);
		List<FakeEmailSender.Sent> sent = mail.sentTo(email);
		Matcher link = LINK.matcher(sent.get(sent.size() - 1).text());
		assertThat(link.find()).isTrue();
		return link.group(1);
	}

	@Test
	void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
		String unknown = ApiClient.uniqueEmail();

		MockHttpServletResponse response = visitor.post(REQUEST, Map.of("email", unknown));

		assertThat(response.getStatus()).isEqualTo(202);
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(mail.sentTo(unknown)).isEmpty();
	}

	@Test
	void aLinkSetsANewPasswordOnceAndSignsTheAccountOutEverywhere() throws Exception {
		assertThat(signedIn.get("/api/v1/auth/me").getStatus()).isEqualTo(200);
		String token = requestLink();
		assertThat(mail.sentTo(email).get(0).text()).contains("http://localhost:5173/reset-password#token=")
			.doesNotContain(ApiClient.PASSWORD);

		MockHttpServletResponse weak = visitor.post(CONFIRM, Map.of("token", token, "newPassword", "short"));
		assertThat(weak.getStatus()).isEqualTo(400);

		assertThat(visitor.post(CONFIRM, Map.of("token", token, "newPassword", NEW_PASSWORD)).getStatus())
			.isEqualTo(204);

		assertThat(signedIn.get("/api/v1/auth/me").getStatus()).isEqualTo(401);
		assertThat(new ApiClient(mockMvc).login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(401);
		assertThat(new ApiClient(mockMvc).login(email, NEW_PASSWORD).getStatus()).isEqualTo(200);

		MockHttpServletResponse reused = visitor.post(CONFIRM, Map.of("token", token, "newPassword", NEW_PASSWORD));
		assertThat(reused.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<String>read(reused.getContentAsString(), "$.code")).isEqualTo("RESET_LINK_INVALID");
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PASSWORD_RESET' and "
				+ "target_id = (select id from user_account where email = ?)", Long.class, email)).isEqualTo(1);
	}

	@Test
	void onlyTheNewestUnexpiredLinkWorks() throws Exception {
		String first = requestLink();
		String second = requestLink();

		assertThat(visitor.post(CONFIRM, Map.of("token", first, "newPassword", NEW_PASSWORD)).getStatus())
			.isEqualTo(400);

		jdbc.update("update password_reset_token set expires_at = now() - interval '1 minute' "
				+ "where user_account_id = (select id from user_account where email = ?)", email);
		assertThat(visitor.post(CONFIRM, Map.of("token", second, "newPassword", NEW_PASSWORD)).getStatus())
			.isEqualTo(400);
		assertThat(visitor.post(CONFIRM, Map.of("token", "made-up-token", "newPassword", NEW_PASSWORD)).getStatus())
			.isEqualTo(400);
	}

	@Test
	void requestsAreLimitedPerAccountWithoutSayingSo() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(visitor.post(REQUEST, Map.of("email", email)).getStatus()).isEqualTo(202);
		}

		assertThat(mail.sentTo(email)).hasSize(3);
	}

	@Test
	void theRequestIsValidated() throws Exception {
		assertThat(visitor.post(REQUEST, Map.of("email", "not an email")).getStatus()).isEqualTo(400);
		assertThat(visitor.post(CONFIRM, Map.of("newPassword", NEW_PASSWORD)).getStatus()).isEqualTo(400);
	}

}
