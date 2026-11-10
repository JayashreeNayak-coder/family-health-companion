package com.familyhealth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.identity.GoogleAccounts.Refusal;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account rules behind Google sign-in (D-028). Google's own login flow can't run in a test, so
 * this calls the service the sign-in handler calls, with the details Google would supply.
 */
class GoogleAccountsIntegrationTest extends IntegrationTestBase {

	@Autowired
	private GoogleAccounts google;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private static String subject() {
		return "google-" + UUID.randomUUID();
	}

	private UUID signedIn(GoogleAccounts.Outcome outcome) {
		assertThat(outcome).isInstanceOf(GoogleAccounts.SignedIn.class);
		return ((GoogleAccounts.SignedIn) outcome).accountId();
	}

	private Refusal refused(GoogleAccounts.Outcome outcome) {
		assertThat(outcome).isInstanceOf(GoogleAccounts.Refused.class);
		return ((GoogleAccounts.Refused) outcome).reason();
	}

	@Test
	void aNewVerifiedEmailCreatesAnAccountWithNoPassword() throws Exception {
		String email = ApiClient.uniqueEmail();
		String subject = subject();

		UUID id = signedIn(google.signIn(subject, email.toUpperCase(), true, "Google Synthetic"));

		assertThat(jdbc.queryForObject("select password_hash is null from user_account where id = ?", Boolean.class, id))
			.isTrue();
		assertThat(jdbc.queryForObject("select email from user_account where id = ?", String.class, id)).isEqualTo(email);
		assertThat(signedIn(google.signIn(subject, email, true, "Google Synthetic"))).isEqualTo(id);
		// No password exists, so a password login can't succeed.
		assertThat(new ApiClient(mockMvc).login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(401);
	}

	@Test
	void aVerifiedEmailLinksToTheExistingAccountAndBothWaysWork() throws Exception {
		String email = ApiClient.uniqueEmail();
		String existing = new ApiClient(mockMvc).signUp(email, "Password Synthetic");

		UUID linked = signedIn(google.signIn(subject(), email, true, "Google Synthetic"));

		assertThat(linked.toString()).isEqualTo(existing);
		assertThat(new ApiClient(mockMvc).login(email, ApiClient.PASSWORD).getStatus()).isEqualTo(200);
		String name = JsonPath.read(jdbc.queryForObject(
				"select json_build_object('n', display_name)::text from user_account where id = ?", String.class, linked),
				"$.n");
		assertThat(name).isEqualTo("Password Synthetic");
	}

	@Test
	void anUnverifiedEmailNeverMatchesOrCreatesAnAccount() throws Exception {
		String email = ApiClient.uniqueEmail();
		new ApiClient(mockMvc).signUp(email, "Password Synthetic");

		assertThat(refused(google.signIn(subject(), email, false, "Someone"))).isEqualTo(Refusal.EMAIL_NOT_VERIFIED);
		String unknown = ApiClient.uniqueEmail();
		assertThat(refused(google.signIn(subject(), unknown, false, "Someone"))).isEqualTo(Refusal.EMAIL_NOT_VERIFIED);
		assertThat(jdbc.queryForObject("select count(*) from user_account where email = ?", Long.class, unknown))
			.isZero();
		assertThat(jdbc.queryForObject("select google_subject is null from user_account where email = ?", Boolean.class,
				email)).isTrue();
	}

	@Test
	void anAccountLinkedToAnotherGoogleIdIsNotTakenOver() {
		String email = ApiClient.uniqueEmail();
		signedIn(google.signIn(subject(), email, true, "First"));

		assertThat(refused(google.signIn(subject(), email, true, "Second")))
			.isEqualTo(Refusal.LINKED_TO_ANOTHER_GOOGLE_ACCOUNT);
	}

	@Test
	void aDisabledAccountCannotSignInWithGoogle() {
		String email = ApiClient.uniqueEmail();
		String subject = subject();
		UUID id = signedIn(google.signIn(subject, email, true, "Synthetic"));
		jdbc.update("update user_account set status = 'DISABLED' where id = ?", id);

		assertThat(refused(google.signIn(subject, email, true, "Synthetic"))).isEqualTo(Refusal.ACCOUNT_DISABLED);
	}

	@Test
	void googleIsOffWithoutKeys() throws Exception {
		ApiClient client = new ApiClient(mockMvc);
		assertThat(JsonPath.<Boolean>read(client.get("/api/v1/auth/providers").getContentAsString(), "$.google"))
			.isFalse();
		assertThat(client.get("/oauth2/authorization/google").getStatus()).isEqualTo(401);
	}

}
