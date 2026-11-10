package com.familyhealth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** With keys set, the app offers Google and sends the browser to Google with the right return address. */
@TestPropertySource(properties = { "app.oauth.google.client-id=synthetic-client-id",
		"app.oauth.google.client-secret=synthetic-client-secret", "app.public-url=https://app.example.test" })
class GoogleSignInConfiguredIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void startingGoogleSignInRedirectsToGoogle() throws Exception {
		ApiClient client = new ApiClient(mockMvc);
		assertThat(JsonPath.<Boolean>read(client.get("/api/v1/auth/providers").getContentAsString(), "$.google"))
			.isTrue();

		MockHttpServletResponse start = client.get("/oauth2/authorization/google");

		assertThat(start.getStatus()).isEqualTo(302);
		String location = URLDecoder.decode(start.getRedirectedUrl(), StandardCharsets.UTF_8);
		assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth")
			.contains("client_id=synthetic-client-id")
			.contains("redirect_uri=https://app.example.test/login/oauth2/code/google")
			.contains("scope=openid profile email")
			.contains("state=")
			.doesNotContain("synthetic-client-secret");
	}

	@Test
	void aCallbackWithoutAStartedSignInGoesBackToSignIn() throws Exception {
		MockHttpServletResponse callback = new ApiClient(mockMvc).get("/login/oauth2/code/google?code=x&state=y");

		assertThat(callback.getStatus()).isEqualTo(302);
		assertThat(callback.getRedirectedUrl()).isEqualTo("https://app.example.test/sign-in?google=failed");
	}

}
