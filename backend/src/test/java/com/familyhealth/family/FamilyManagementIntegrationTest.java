package com.familyhealth.family;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Renaming, unique names per account, and deleting a family (DECISIONS.md, D-031). */
class FamilyManagementIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ApiClient signedUp() throws Exception {
		ApiClient client = new ApiClient(mockMvc);
		client.signUp(ApiClient.uniqueEmail(), "Synthetic Person");
		return client;
	}

	private static String id(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private static long version(ApiClient client, String familyId) throws Exception {
		return ((Number) JsonPath.read(client.get("/api/v1/families/" + familyId).getContentAsString(), "$.version"))
			.longValue();
	}

	private static MockHttpServletResponse rename(ApiClient client, String familyId, String name, long version)
			throws Exception {
		return client.put("/api/v1/families/" + familyId, Map.of("name", name, "version", version));
	}

	@Test
	void anAccountCannotHaveTwoFamiliesWithTheSameNameIgnoringCaseAndSpaces() throws Exception {
		ApiClient client = signedUp();
		id(client.post("/api/v1/families", Map.of("name", "Sharma Family")));

		MockHttpServletResponse duplicate = client.post("/api/v1/families", Map.of("name", "  sharma   FAMILY "));

		assertThat(duplicate.getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(duplicate.getContentAsString(), "$.code")).isEqualTo("FAMILY_NAME_TAKEN");
		assertThat(client.post("/api/v1/families", Map.of("name", "Sharma Family 2")).getStatus()).isEqualTo(201);
	}

	@Test
	void differentAccountsMayUseTheSameNameWithoutLearningOfEachOther() throws Exception {
		String name = "Common Family " + System.nanoTime();
		assertThat(signedUp().post("/api/v1/families", Map.of("name", name)).getStatus()).isEqualTo(201);
		assertThat(signedUp().post("/api/v1/families", Map.of("name", name)).getStatus()).isEqualTo(201);
	}

	@Test
	void aNameIsStoredWithSpacesTidied() throws Exception {
		ApiClient client = signedUp();
		String familyId = id(client.post("/api/v1/families", Map.of("name", "  The   Rao\tFamily ")));
		assertThat(JsonPath.<String>read(client.get("/api/v1/families/" + familyId).getContentAsString(), "$.name"))
			.isEqualTo("The Rao Family");
	}

	@Test
	void aManagerCanRenameAndOthersCannot() throws Exception {
		ProfileFixture f = new ProfileFixture(mockMvc);
		String path = "/api/v1/families/" + f.familyId;
		long version = version(f.manager, f.familyId);

		assertThat(rename(f.viewer, f.familyId, "Renamed by member", version).getStatus()).isEqualTo(403);
		assertThat(rename(f.outsider, f.familyId, "Renamed by outsider", version).getStatus()).isEqualTo(404);

		MockHttpServletResponse renamed = rename(f.manager, f.familyId, "Renamed Family", version);
		assertThat(renamed.getStatus()).as(renamed.getContentAsString()).isEqualTo(200);
		assertThat(JsonPath.<String>read(f.viewer.get(path).getContentAsString(), "$.name")).isEqualTo("Renamed Family");
		assertThat(rename(f.manager, f.familyId, "Stale rename", version).getStatus()).isEqualTo(409);

		Map<String, Object> blank = new HashMap<>(Map.of("name", "   ", "version", version + 1));
		assertThat(f.manager.put(path, blank).getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'FAMILY_RENAMED' and target_id = ?::uuid",
				Long.class, f.familyId)).isEqualTo(1);
	}

	@Test
	void aRenameCannotClashWithAnotherFamilyOfAnyMember() throws Exception {
		ProfileFixture f = new ProfileFixture(mockMvc);
		String viewersOther = "Viewer's other family " + System.nanoTime();
		assertThat(f.viewer.post("/api/v1/families", Map.of("name", viewersOther)).getStatus()).isEqualTo(201);

		MockHttpServletResponse clash = rename(f.manager, f.familyId, viewersOther.toUpperCase(),
				version(f.manager, f.familyId));

		assertThat(clash.getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(clash.getContentAsString(), "$.code")).isEqualTo("FAMILY_NAME_TAKEN");
	}

	@Test
	void joiningAFamilyWhoseNameYouAlreadyUseIsRefused() throws Exception {
		ApiClient manager = signedUp();
		String name = "Joined Family " + System.nanoTime();
		String familyId = id(manager.post("/api/v1/families", Map.of("name", name)));
		ApiClient invited = new ApiClient(mockMvc);
		String email = ApiClient.uniqueEmail();
		invited.signUp(email, "Invited Synthetic");
		assertThat(invited.post("/api/v1/families", Map.of("name", name)).getStatus()).isEqualTo(201);
		String token = JsonPath.read(manager.post("/api/v1/families/" + familyId + "/invitations", Map.of("email", email))
			.getContentAsString(), "$.token");

		MockHttpServletResponse accepted = invited.post("/api/v1/families/invitations/" + token + "/accept", Map.of());

		assertThat(accepted.getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(accepted.getContentAsString(), "$.code")).isEqualTo("FAMILY_NAME_TAKEN");
	}

	@Test
	void aFamilyWithOtherMembersCannotBeDeleted() throws Exception {
		ProfileFixture f = new ProfileFixture(mockMvc);
		String path = "/api/v1/families/" + f.familyId;

		assertThat(f.viewer.delete(path).getStatus()).isEqualTo(403);
		assertThat(f.outsider.delete(path).getStatus()).isEqualTo(404);
		MockHttpServletResponse refused = f.manager.delete(path);
		assertThat(refused.getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(refused.getContentAsString(), "$.code")).isEqualTo("FAMILY_HAS_OTHER_MEMBERS");
		assertThat(f.manager.get(path).getStatus()).isEqualTo(200);
	}

	@Test
	void deletingAFamilyDeletesItsProfilesRecordsAndCards() throws Exception {
		ApiClient client = signedUp();
		String familyId = id(client.post("/api/v1/families", Map.of("name", "Doomed Family")));
		String child = id(client.post("/api/v1/families/" + familyId + "/profiles",
				Map.of("fullName", "Child Synthetic", "kind", "DEPENDENT")));
		String self = id(client.post("/api/v1/families/" + familyId + "/profiles",
				Map.of("fullName", "Self Synthetic", "kind", "SELF")));
		id(client.post("/api/v1/profiles/" + child + "/allergies", Map.of("allergen", "Synthetic dust")));
		assertThat(client.put("/api/v1/profiles/" + child + "/emergency-card",
				Map.of("items", List.of(Map.of("type", "NOTE", "text", "Synthetic note")))).getStatus()).isEqualTo(200);

		assertThat(client.delete("/api/v1/families/" + familyId).getStatus()).isEqualTo(204);

		assertThat(client.get("/api/v1/families/" + familyId).getStatus()).isEqualTo(404);
		assertThat(client.get("/api/v1/profiles/" + child).getStatus()).isEqualTo(404);
		for (String table : List.of("health_profile", "allergy", "emergency_card")) {
			String column = table.equals("health_profile") ? "id" : "profile_id";
			assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + column + " in (?::uuid, ?::uuid)",
					Long.class, child, self)).as(table).isZero();
		}
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_event where action = 'PROFILE_DELETED' and profile_id in (?::uuid, ?::uuid)",
				Long.class, child, self)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'FAMILY_DELETED' and target_id = ?::uuid",
				Long.class, familyId)).isEqualTo(1);
		assertThat(client.post("/api/v1/families", Map.of("name", "Doomed Family")).getStatus()).isEqualTo(201);
	}

}
