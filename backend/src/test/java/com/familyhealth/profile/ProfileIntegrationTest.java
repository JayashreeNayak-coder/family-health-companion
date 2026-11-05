package com.familyhealth.profile;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class ProfileIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ProfileFixture f;

	private String profilePath;

	private String grantsPath;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		profilePath = "/api/v1/profiles/" + f.profileId;
		grantsPath = profilePath + "/grants";
	}

	private long version() throws Exception {
		return ((Number) JsonPath.read(f.manager.get(profilePath).getContentAsString(), "$.version")).longValue();
	}

	private Map<String, Object> updateBody(String name) throws Exception {
		return Map.of("fullName", name, "version", version());
	}

	@Test
	void profileReadMatrix() throws Exception {
		assertThat(f.signedOut.get(profilePath).getStatus()).isEqualTo(401);
		assertThat(f.outsider.get(profilePath).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(profilePath).getStatus()).isEqualTo(404);
		assertThat(f.viewer.get(profilePath).getStatus()).isEqualTo(200);
		assertThat(f.editor.get(profilePath).getStatus()).isEqualTo(200);
		assertThat(f.manager.get(profilePath).getStatus()).isEqualTo(200);
	}

	@Test
	void profileUpdateMatrix() throws Exception {
		Map<String, Object> body = updateBody("Renamed Synthetic");

		assertThat(f.signedOut.put(profilePath, body).getStatus()).isEqualTo(401);
		assertThat(f.outsider.put(profilePath, body).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.put(profilePath, body).getStatus()).isEqualTo(404);
		assertThat(f.viewer.put(profilePath, body).getStatus()).isEqualTo(403);
		assertThat(read(f.manager.get(profilePath), "$.fullName")).isEqualTo("Child Synthetic");

		MockHttpServletResponse updated = f.editor.put(profilePath, body);
		assertThat(updated.getStatus()).isEqualTo(200);
		assertThat(read(updated, "$.fullName")).isEqualTo("Renamed Synthetic");
	}

	@Test
	void staleVersionIsRejected() throws Exception {
		Map<String, Object> first = updateBody("First Synthetic");
		assertThat(f.manager.put(profilePath, first).getStatus()).isEqualTo(200);

		MockHttpServletResponse stale = f.manager.put(profilePath, first);

		assertThat(stale.getStatus()).isEqualTo(409);
		assertThat(read(stale, "$.code")).isEqualTo("STALE_VERSION");
	}

	@Test
	void profileDeleteMatrixAndAudit() throws Exception {
		assertThat(f.signedOut.delete(profilePath).getStatus()).isEqualTo(401);
		assertThat(f.outsider.delete(profilePath).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.delete(profilePath).getStatus()).isEqualTo(404);
		assertThat(f.viewer.delete(profilePath).getStatus()).isEqualTo(403);
		assertThat(f.editor.delete(profilePath).getStatus()).isEqualTo(403);
		assertThat(f.manager.get(profilePath).getStatus()).isEqualTo(200);

		assertThat(f.manager.delete(profilePath).getStatus()).isEqualTo(204);

		assertThat(f.manager.get(profilePath).getStatus()).isEqualTo(404);
		assertThat(auditCount("PROFILE_DELETED", f.profileId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from profile_access_grant where profile_id = ?::uuid",
				Long.class, f.profileId)).isZero();
	}

	@Test
	void deniedAttemptsAreAuditedAndHoldNoNames() throws Exception {
		f.outsider.get(profilePath);
		f.viewer.delete(profilePath);

		assertThat(auditCount("ACCESS_DENIED", f.profileId)).isEqualTo(2);
		String metadata = jdbc.queryForList(
				"select metadata::text from audit_event where profile_id = ?::uuid", String.class, f.profileId)
			.toString();
		assertThat(metadata).doesNotContain("Synthetic");
	}

	@Test
	void listReturnsOnlyProfilesWithAGrant() throws Exception {
		assertThat(ids(f.manager)).contains(f.profileId, f.otherProfileId);
		assertThat(ids(f.viewer)).containsExactly(f.profileId);
		assertThat(ids(f.memberWithoutGrant)).isEmpty();
		assertThat(ids(f.outsider)).isEmpty();
		assertThat(f.signedOut.get("/api/v1/profiles").getStatus()).isEqualTo(401);
		assertThat(f.manager.get("/api/v1/profiles?size=101").getStatus()).isEqualTo(400);

		String level = JsonPath.read(f.viewer.get("/api/v1/profiles").getContentAsString(),
				"$.items[0].accessLevel");
		assertThat(level).isEqualTo("VIEW");
	}

	private List<String> ids(ApiClient client) throws Exception {
		return JsonPath.read(client.get("/api/v1/profiles").getContentAsString(), "$.items[*].id");
	}

	@Test
	void onlyAManagerCreatesProfilesForOthersButAnyMemberCreatesTheirOwn() throws Exception {
		String path = "/api/v1/families/" + f.familyId + "/profiles";
		Map<String, String> dependent = Map.of("fullName", "New Child", "kind", "DEPENDENT");
		Map<String, String> self = Map.of("fullName", "Member Self", "kind", "SELF");

		assertThat(f.signedOut.post(path, dependent).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(path, dependent).getStatus()).isEqualTo(404);
		assertThat(f.outsider.post(path, self).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.post(path, dependent).getStatus()).isEqualTo(403);

		MockHttpServletResponse own = created(f.memberWithoutGrant.post(path, self));
		assertThat(read(own, "$.accessLevel")).isEqualTo("MANAGE");
		assertThat(JsonPath.<Boolean>read(own.getContentAsString(), "$.linkedToAccount")).isTrue();

		MockHttpServletResponse second = f.memberWithoutGrant.post(path, self);
		assertThat(second.getStatus()).isEqualTo(409);
		assertThat(read(second, "$.code")).isEqualTo("SELF_PROFILE_EXISTS");
	}

	@Test
	void aManagerHasNoAutomaticAccessToAnotherAdultsOwnProfile() throws Exception {
		String path = "/api/v1/families/" + f.familyId + "/profiles";
		String own = read(created(f.memberWithoutGrant.post(path, Map.of("fullName", "Adult Self", "kind", "SELF"))),
				"$.id");

		assertThat(f.manager.get("/api/v1/profiles/" + own).getStatus()).isEqualTo(404);
		assertThat(ids(f.manager)).doesNotContain(own);
	}

	@Test
	void validationUsesTheStandardFormat() throws Exception {
		String path = "/api/v1/families/" + f.familyId + "/profiles";

		MockHttpServletResponse blank = f.manager.post(path, Map.of("fullName", " ", "kind", "DEPENDENT"));
		assertThat(blank.getStatus()).isEqualTo(400);
		assertThat(read(blank, "$.code")).isEqualTo("VALIDATION_FAILED");
		assertThat(read(blank, "$.errors[0].field")).isEqualTo("fullName");

		MockHttpServletResponse future = f.manager.post(path,
				Map.of("fullName", "Future Child", "kind", "DEPENDENT", "dateOfBirth", "2999-01-01"));
		assertThat(future.getStatus()).isEqualTo(400);
		assertThat(f.manager.post(path, Map.of("fullName", "No Kind")).getStatus()).isEqualTo(400);
	}

	@Test
	void grantEndpointsRequireManage() throws Exception {
		String target = grantsPath + "/" + f.memberWithoutGrantId;
		Map<String, String> body = Map.of("level", "VIEW");

		for (ApiClient denied : List.of(f.viewer, f.editor)) {
			assertThat(denied.get(grantsPath).getStatus()).isEqualTo(403);
			assertThat(denied.put(target, body).getStatus()).isEqualTo(403);
			assertThat(denied.delete(grantsPath + "/" + f.viewerId).getStatus()).isEqualTo(403);
		}
		for (ApiClient hidden : List.of(f.outsider, f.memberWithoutGrant)) {
			assertThat(hidden.get(grantsPath).getStatus()).isEqualTo(404);
			assertThat(hidden.put(target, body).getStatus()).isEqualTo(404);
			assertThat(hidden.delete(grantsPath + "/" + f.viewerId).getStatus()).isEqualTo(404);
		}
		assertThat(f.signedOut.get(grantsPath).getStatus()).isEqualTo(401);
		assertThat(f.memberWithoutGrant.get(profilePath).getStatus()).isEqualTo(404);
	}

	@Test
	void grantingGivesAccessAndRevokingRemovesItWithAudit() throws Exception {
		String target = grantsPath + "/" + f.memberWithoutGrantId;

		assertThat(f.manager.put(target, Map.of("level", "VIEW")).getStatus()).isEqualTo(200);
		assertThat(f.memberWithoutGrant.get(profilePath).getStatus()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(f.manager.get(grantsPath).getContentAsString(), "$[*].accountId"))
			.contains(f.memberWithoutGrantId);

		assertThat(f.manager.delete(target).getStatus()).isEqualTo(204);
		assertThat(f.memberWithoutGrant.get(profilePath).getStatus()).isEqualTo(404);

		assertThat(auditCount("GRANT_CHANGED", f.profileId)).isGreaterThanOrEqualTo(1);
		assertThat(auditCount("GRANT_REVOKED", f.profileId)).isEqualTo(1);
	}

	@Test
	void grantsCanOnlyGoToFamilyMembers() throws Exception {
		MockHttpServletResponse outsider = f.manager.put(grantsPath + "/" + f.outsiderId, Map.of("level", "VIEW"));
		assertThat(outsider.getStatus()).isEqualTo(404);
		assertThat(read(outsider, "$.code")).isEqualTo("MEMBER_NOT_FOUND");

		assertThat(f.manager.put(grantsPath + "/" + UUID.randomUUID(), Map.of("level", "VIEW")).getStatus())
			.isEqualTo(404);
		assertThat(f.outsider.get(profilePath).getStatus()).isEqualTo(404);
	}

	@Test
	void theLastManageGrantCannotBeRemovedOrLowered() throws Exception {
		String own = grantsPath + "/" + f.managerId;

		MockHttpServletResponse lowered = f.manager.put(own, Map.of("level", "EDIT"));
		assertThat(lowered.getStatus()).isEqualTo(409);
		assertThat(read(lowered, "$.code")).isEqualTo("LAST_MANAGER");
		assertThat(f.manager.delete(own).getStatus()).isEqualTo(409);

		// With a second MANAGE holder the first may step down.
		f.grant(f.profileId, f.editorId, "MANAGE");
		assertThat(f.manager.put(own, Map.of("level", "EDIT")).getStatus()).isEqualTo(200);
		assertThat(f.editor.delete(grantsPath + "/" + f.editorId).getStatus()).isEqualTo(409);
	}

	@Test
	void aSecondManagerCanRemoveTheFirst() throws Exception {
		f.grant(f.profileId, f.editorId, "MANAGE");

		assertThat(f.editor.delete(grantsPath + "/" + f.managerId).getStatus()).isEqualTo(204);

		assertThat(f.manager.get(profilePath).getStatus()).isEqualTo(404);
	}

	private Long auditCount(String action, String profileId) {
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and profile_id = ?::uuid",
				Long.class, action, profileId);
	}

}
