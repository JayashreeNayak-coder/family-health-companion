package com.familyhealth.family;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.familyhealth.common.SecureTokens;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class FamilyIntegrationTest extends IntegrationTestBase {

	private static final String FAMILIES = "/api/v1/families";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ProfileFixture f;

	private String familyPath;

	private String invitationsPath;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		familyPath = FAMILIES + "/" + f.familyId;
		invitationsPath = familyPath + "/invitations";
	}

	private record Invitee(ApiClient client, String email, String id) {
	}

	private Invitee newAccount() throws Exception {
		ApiClient client = new ApiClient(mockMvc);
		String email = ApiClient.uniqueEmail();
		return new Invitee(client, email, client.signUp(email, "Invitee Synthetic"));
	}

	private String invite(Map<String, ?> body) throws Exception {
		return read(created(f.manager.post(invitationsPath, body)), "$.token");
	}

	private static String acceptPath(String token) {
		return FAMILIES + "/invitations/" + token + "/accept";
	}

	@Test
	void creatorBecomesManagerAndFamilyIsListedOnlyForMembers() throws Exception {
		assertThat(f.signedOut.post(FAMILIES, Map.of("name", "X")).getStatus()).isEqualTo(401);
		assertThat(f.manager.post(FAMILIES, Map.of("name", " ")).getStatus()).isEqualTo(400);

		assertThat(read(f.manager.get(familyPath), "$.role")).isEqualTo("MANAGER");
		assertThat(read(f.viewer.get(familyPath), "$.role")).isEqualTo("MEMBER");
		assertThat(f.outsider.get(familyPath).getStatus()).isEqualTo(404);
		assertThat(f.signedOut.get(familyPath).getStatus()).isEqualTo(401);

		assertThat(JsonPath.<List<String>>read(f.viewer.get(FAMILIES).getContentAsString(), "$.items[*].id"))
			.containsExactly(f.familyId);
		assertThat(JsonPath.<List<String>>read(f.outsider.get(FAMILIES).getContentAsString(), "$.items[*].id"))
			.isEmpty();
	}

	@Test
	void onlyAManagerCanInvite() throws Exception {
		Map<String, String> body = Map.of("email", ApiClient.uniqueEmail());

		assertThat(f.signedOut.post(invitationsPath, body).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(invitationsPath, body).getStatus()).isEqualTo(404);
		assertThat(f.viewer.post(invitationsPath, body).getStatus()).isEqualTo(403);
		assertThat(f.manager.post(invitationsPath, Map.of("email", "not-an-email")).getStatus()).isEqualTo(400);
		assertThat(f.manager.post(invitationsPath, body).getStatus()).isEqualTo(201);
	}

	@Test
	void onlyTheTokensHashIsStored() throws Exception {
		String token = invite(Map.of("email", ApiClient.uniqueEmail()));

		assertThat(jdbc.queryForObject("select count(*) from family_invitation where token_hash = ?", Long.class,
				SecureTokens.hash(token))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from family_invitation where token_hash = ?", Long.class,
				token)).isZero();
	}

	@Test
	void anInvitationWorksOnceAndOnlyForTheInvitedEmail() throws Exception {
		Invitee invitee = newAccount();
		String token = invite(Map.of("email", invitee.email().toUpperCase()));

		// Someone else holding the token can't use it, or even read it.
		assertThat(f.outsider.post(acceptPath(token), Map.of()).getStatus()).isEqualTo(404);
		assertThat(f.outsider.get(FAMILIES + "/invitations/" + token).getStatus()).isEqualTo(404);
		assertThat(f.signedOut.post(acceptPath(token), Map.of()).getStatus()).isEqualTo(401);

		MockHttpServletResponse preview = invitee.client().get(FAMILIES + "/invitations/" + token);
		assertThat(read(preview, "$.familyName")).isEqualTo("Synthetic Family");

		assertThat(invitee.client().post(acceptPath(token), Map.of()).getStatus()).isEqualTo(200);
		assertThat(read(invitee.client().get(familyPath), "$.role")).isEqualTo("MEMBER");
		// Joining gives no access to anyone's records.
		assertThat(invitee.client().get("/api/v1/profiles/" + f.profileId).getStatus()).isEqualTo(404);

		assertThat(invitee.client().post(acceptPath(token), Map.of()).getStatus()).isEqualTo(404);
	}

	@Test
	void expiredAndUnknownInvitationsLookTheSame() throws Exception {
		Invitee invitee = newAccount();
		String token = invite(Map.of("email", invitee.email()));
		jdbc.update("update family_invitation set expires_at = now() - interval '1 minute' where token_hash = ?",
				SecureTokens.hash(token));

		MockHttpServletResponse expired = invitee.client().post(acceptPath(token), Map.of());
		MockHttpServletResponse unknown = invitee.client().post(acceptPath(SecureTokens.generate()), Map.of());

		assertThat(expired.getStatus()).isEqualTo(404);
		assertThat(unknown.getStatus()).isEqualTo(404);
		assertThat(read(expired, "$.code")).isEqualTo(read(unknown, "$.code"));
	}

	@Test
	void anExistingMemberCannotBeInvitedAgain() throws Exception {
		Invitee invitee = newAccount();
		String token = invite(Map.of("email", invitee.email()));
		invitee.client().post(acceptPath(token), Map.of());

		MockHttpServletResponse again = f.manager.post(invitationsPath, Map.of("email", invitee.email()));

		assertThat(again.getStatus()).isEqualTo(409);
		assertThat(read(again, "$.code")).isEqualTo("ALREADY_MEMBER");
	}

	@Test
	void linkingAProfileAsksTheAdultAndDropsOtherAccessToTheirChoice() throws Exception {
		Invitee adult = newAccount();
		String token = invite(Map.of("email", adult.email(), "profileId", f.profileId));
		String path = "/api/v1/profiles/" + f.profileId;

		assertThat(read(adult.client().get(FAMILIES + "/invitations/" + token), "$.profileName"))
			.isEqualTo("Child Synthetic");

		MockHttpServletResponse noChoice = adult.client().post(acceptPath(token), Map.of());
		assertThat(noChoice.getStatus()).isEqualTo(400);
		assertThat(read(noChoice, "$.code")).isEqualTo("KEEP_ACCESS_REQUIRED");
		// The failed accept changed nothing.
		assertThat(adult.client().get(familyPath).getStatus()).isEqualTo(404);

		assertThat(adult.client().post(acceptPath(token), Map.of("keepAccess", "VIEW")).getStatus()).isEqualTo(200);

		assertThat(read(adult.client().get(path), "$.accessLevel")).isEqualTo("MANAGE");
		assertThat(JsonPath.<Boolean>read(adult.client().get(path).getContentAsString(), "$.dependent")).isFalse();
		assertThat(read(f.manager.get(path), "$.accessLevel")).isEqualTo("VIEW");
		assertThat(read(f.editor.get(path), "$.accessLevel")).isEqualTo("VIEW");
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PROFILE_LINKED' "
				+ "and profile_id = ?::uuid", Long.class, f.profileId)).isEqualTo(1);
	}

	@Test
	void anAdultCanRemoveEveryoneElseWhenLinking() throws Exception {
		Invitee adult = newAccount();
		String token = invite(Map.of("email", adult.email(), "profileId", f.profileId));

		adult.client().post(acceptPath(token), Map.of("keepAccess", "NONE"));

		String path = "/api/v1/profiles/" + f.profileId;
		assertThat(f.manager.get(path).getStatus()).isEqualTo(404);
		assertThat(f.viewer.get(path).getStatus()).isEqualTo(404);
	}

	@Test
	void theLinkedAdultsAccessCannotBeTakenAway() throws Exception {
		Invitee adult = newAccount();
		String token = invite(Map.of("email", adult.email(), "profileId", f.profileId));
		adult.client().post(acceptPath(token), Map.of("keepAccess", "EDIT"));
		String grants = "/api/v1/profiles/" + f.profileId + "/grants/";
		adult.client().put(grants + f.managerId, Map.of("level", "MANAGE"));

		MockHttpServletResponse lowered = f.manager.put(grants + adult.id(), Map.of("level", "VIEW"));

		assertThat(lowered.getStatus()).isEqualTo(409);
		assertThat(read(lowered, "$.code")).isEqualTo("LINKED_ACCOUNT_GRANT");
		assertThat(f.manager.delete(grants + adult.id()).getStatus()).isEqualTo(409);
	}

	@Test
	void offeringAProfileRequiresManageOnItAndAnUnlinkedProfileInThisFamily() throws Exception {
		Invitee adult = newAccount();
		Map<String, String> body = Map.of("email", adult.email(), "profileId", f.profileId);

		// A family manager without MANAGE on this adult's profile can't give it away.
		f.grant(f.profileId, f.editorId, "MANAGE");
		f.manager.put("/api/v1/profiles/" + f.profileId + "/grants/" + f.managerId, Map.of("level", "VIEW"));
		assertThat(f.manager.post(invitationsPath, body).getStatus()).isEqualTo(403);

		// A profile from another family looks missing.
		String otherFamily = read(created(f.outsider.post(FAMILIES, Map.of("name", "Other"))), "$.id");
		String otherProfile = read(created(f.outsider.post(FAMILIES + "/" + otherFamily + "/profiles",
				Map.of("fullName", "Other Child", "kind", "DEPENDENT"))), "$.id");
		assertThat(f.outsider.post(FAMILIES + "/" + otherFamily + "/invitations",
				Map.of("email", adult.email(), "profileId", f.otherProfileId)).getStatus()).isEqualTo(404);
		assertThat(f.manager.post(invitationsPath, Map.of("email", adult.email(), "profileId", otherProfile))
			.getStatus()).isEqualTo(404);
	}

}
