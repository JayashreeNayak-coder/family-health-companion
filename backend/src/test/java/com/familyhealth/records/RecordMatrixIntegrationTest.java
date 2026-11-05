package com.familyhealth.records;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The authorization matrix from docs/07 section 3, on every profile-owned collection. A new
 * record type joins by adding a row to {@link #resources()}.
 */
class RecordMatrixIntegrationTest extends IntegrationTestBase {

	/** A collection path, a valid body, a field to change, and an invalid body. */
	record Resource(String path, Map<String, Object> valid, String field, Map<String, Object> invalid) {

		@Override
		public String toString() {
			return path;
		}

	}

	static Stream<Resource> resources() {
		return Stream.of(
				new Resource("conditions", Map.of("name", "Synthetic condition", "status", "ACTIVE"), "name",
						Map.of("name", " ")),
				new Resource("allergies", Map.of("allergen", "Synthetic allergen"), "allergen",
						Map.of("allergen", "")),
				new Resource("dietary-restrictions", Map.of("kind", "PREFERENCE", "description", "Synthetic"),
						"description", Map.of("kind", "PREFERENCE")),
				new Resource("emergency-contacts", Map.of("name", "Synthetic Contact", "phone", "+91 555 0100"), "name",
						Map.of("name", "Synthetic Contact", "phone", "not a phone")),
				new Resource("visits", Map.of("occurredOn", "2026-01-15", "occurredTime", "08:30", "reason", "Checkup"),
						"reason", Map.of("occurredTime", "08:30")),
				new Resource("symptoms", Map.of("description", "Synthetic cough", "occurredOn", "2026-01-15"),
						"description", Map.of("description", " ")));
	}

	@Autowired
	private MockMvc mockMvc;

	private ProfileFixture f;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
	}

	private String collection(String profileId, Resource resource) {
		return "/api/v1/profiles/" + profileId + "/" + resource.path();
	}

	private String createRecord(Resource resource) throws Exception {
		return read(created(f.editor.post(collection(f.profileId, resource), resource.valid())), "$.id");
	}

	private static Map<String, Object> changed(Resource resource, long version) {
		Map<String, Object> body = new HashMap<>(resource.valid());
		body.put(resource.field(), "Changed");
		body.put("version", version);
		return body;
	}

	private int count(Resource resource) throws Exception {
		return JsonPath.read(f.manager.get(collection(f.profileId, resource)).getContentAsString(), "$.totalItems");
	}

	@ParameterizedTest
	@MethodSource("resources")
	void readMatrix(Resource resource) throws Exception {
		String list = collection(f.profileId, resource);
		String one = list + "/" + createRecord(resource);

		for (String path : List.of(list, one)) {
			assertThat(f.signedOut.get(path).getStatus()).isEqualTo(401);
			assertThat(f.outsider.get(path).getStatus()).isEqualTo(404);
			assertThat(f.memberWithoutGrant.get(path).getStatus()).isEqualTo(404);
			assertThat(f.viewer.get(path).getStatus()).isEqualTo(200);
			assertThat(f.editor.get(path).getStatus()).isEqualTo(200);
		}
	}

	@ParameterizedTest
	@MethodSource("resources")
	void createMatrix(Resource resource) throws Exception {
		String list = collection(f.profileId, resource);

		assertThat(f.signedOut.post(list, resource.valid()).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(list, resource.valid()).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.post(list, resource.valid()).getStatus()).isEqualTo(404);
		assertThat(f.viewer.post(list, resource.valid()).getStatus()).isEqualTo(403);
		assertThat(count(resource)).isZero();

		assertThat(f.editor.post(list, resource.valid()).getStatus()).isEqualTo(201);
		assertThat(count(resource)).isEqualTo(1);
	}

	@ParameterizedTest
	@MethodSource("resources")
	void updateMatrixAndStaleVersion(Resource resource) throws Exception {
		String one = collection(f.profileId, resource) + "/" + createRecord(resource);
		Map<String, Object> body = changed(resource, 0);

		assertThat(f.signedOut.put(one, body).getStatus()).isEqualTo(401);
		assertThat(f.outsider.put(one, body).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.put(one, body).getStatus()).isEqualTo(404);
		assertThat(f.viewer.put(one, body).getStatus()).isEqualTo(403);
		assertThat(JsonPath.<Object>read(f.manager.get(one).getContentAsString(), "$." + resource.field()))
			.isNotEqualTo("Changed");

		MockHttpServletResponse updated = f.editor.put(one, body);
		assertThat(updated.getStatus()).isEqualTo(200);
		assertThat(read(updated, "$." + resource.field())).isEqualTo("Changed");

		MockHttpServletResponse stale = f.editor.put(one, body);
		assertThat(stale.getStatus()).isEqualTo(409);
		assertThat(read(stale, "$.code")).isEqualTo("STALE_VERSION");
	}

	@ParameterizedTest
	@MethodSource("resources")
	void deleteMatrix(Resource resource) throws Exception {
		String one = collection(f.profileId, resource) + "/" + createRecord(resource);

		assertThat(f.signedOut.delete(one).getStatus()).isEqualTo(401);
		assertThat(f.outsider.delete(one).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.delete(one).getStatus()).isEqualTo(404);
		assertThat(f.viewer.delete(one).getStatus()).isEqualTo(403);
		assertThat(count(resource)).isEqualTo(1);

		assertThat(f.editor.delete(one).getStatus()).isEqualTo(204);
		assertThat(count(resource)).isZero();
		assertThat(f.editor.get(one).getStatus()).isEqualTo(404);
	}

	@ParameterizedTest
	@MethodSource("resources")
	void aRecordIdFromAnotherProfileReturns404(Resource resource) throws Exception {
		String recordId = createRecord(resource);
		f.grant(f.otherProfileId, f.editorId, "EDIT");
		String wrong = collection(f.otherProfileId, resource) + "/" + recordId;

		// The editor has a valid grant on both profiles, and the ID still has to match its profile.
		assertThat(f.editor.get(wrong).getStatus()).isEqualTo(404);
		assertThat(f.editor.put(wrong, changed(resource, 0)).getStatus()).isEqualTo(404);
		assertThat(f.editor.delete(wrong).getStatus()).isEqualTo(404);
		assertThat(count(resource)).isEqualTo(1);
	}

	@ParameterizedTest
	@MethodSource("resources")
	void invalidInputUsesTheStandardFormat(Resource resource) throws Exception {
		MockHttpServletResponse response = f.editor.post(collection(f.profileId, resource), resource.invalid());

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(read(response, "$.code")).isEqualTo("VALIDATION_FAILED");
		assertThat(JsonPath.<List<?>>read(response.getContentAsString(), "$.errors")).isNotEmpty();
		assertThat(count(resource)).isZero();
	}

	@ParameterizedTest
	@MethodSource("resources")
	void deletingTheProfileRemovesItsRecords(Resource resource) throws Exception {
		String recordId = createRecord(resource);

		assertThat(f.manager.delete("/api/v1/profiles/" + f.profileId).getStatus()).isEqualTo(204);

		assertThat(f.manager.get(collection(f.profileId, resource) + "/" + recordId).getStatus()).isEqualTo(404);
	}

}
