package com.familyhealth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One family built through the real API: a manager holding MANAGE on two dependents' profiles, a
 * VIEW grantee, an EDIT grantee, a member with no grant, and an outsider.
 */
public final class ProfileFixture {

	public final ApiClient manager;

	public final ApiClient viewer;

	public final ApiClient editor;

	public final ApiClient memberWithoutGrant;

	public final ApiClient outsider;

	public final ApiClient signedOut;

	public final String managerId;

	public final String viewerId;

	public final String editorId;

	public final String memberWithoutGrantId;

	public final String outsiderId;

	public final String familyId;

	public final String profileId;

	public final String otherProfileId;

	public ProfileFixture(MockMvc mockMvc) throws Exception {
		manager = new ApiClient(mockMvc);
		viewer = new ApiClient(mockMvc);
		editor = new ApiClient(mockMvc);
		memberWithoutGrant = new ApiClient(mockMvc);
		outsider = new ApiClient(mockMvc);
		signedOut = new ApiClient(mockMvc);

		managerId = manager.signUp(ApiClient.uniqueEmail(), "Manager Synthetic");
		familyId = read(created(manager.post("/api/v1/families", Map.of("name", "Synthetic Family"))), "$.id");

		viewerId = join(viewer, "Viewer Synthetic");
		editorId = join(editor, "Editor Synthetic");
		memberWithoutGrantId = join(memberWithoutGrant, "Member Synthetic");
		outsiderId = outsider.signUp(ApiClient.uniqueEmail(), "Outsider Synthetic");

		profileId = createDependent("Child Synthetic");
		otherProfileId = createDependent("Other Child Synthetic");
		grant(profileId, viewerId, "VIEW");
		grant(profileId, editorId, "EDIT");
	}

	public String createDependent(String name) throws Exception {
		return read(created(manager.post("/api/v1/families/" + familyId + "/profiles",
				Map.of("fullName", name, "kind", "DEPENDENT"))), "$.id");
	}

	public void grant(String profile, String accountId, String level) throws Exception {
		MockHttpServletResponse response = manager.put("/api/v1/profiles/" + profile + "/grants/" + accountId,
				Map.of("level", level));
		assertThat(response.getStatus()).isEqualTo(200);
	}

	public static String read(MockHttpServletResponse response, String path) throws Exception {
		return JsonPath.read(response.getContentAsString(), path);
	}

	public static MockHttpServletResponse created(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return response;
	}

	private String join(ApiClient client, String name) throws Exception {
		String email = ApiClient.uniqueEmail();
		String id = client.signUp(email, name);
		String token = read(created(manager.post("/api/v1/families/" + familyId + "/invitations",
				Map.of("email", email))), "$.token");
		assertThat(client.post("/api/v1/families/invitations/" + token + "/accept", Map.of()).getStatus())
			.isEqualTo(200);
		return id;
	}

}
