package com.familyhealth.records;

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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class AttachmentIntegrationTest extends IntegrationTestBase {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

	@Autowired
	private MockMvc mockMvc;

	private ProfileFixture f;

	private String base;

	private String attachments;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		base = "/api/v1/profiles/" + f.profileId;
		attachments = base + "/attachments";
	}

	private String upload(String profileId) throws Exception {
		return read(created(f.manager.upload("/api/v1/profiles/" + profileId + "/documents", "proof.png",
				"image/png", PNG, Map.of("category", "PHOTO"))), "$.id");
	}

	private String visit() throws Exception {
		return read(created(f.editor.post(base + "/visits", Map.of("reason", "Synthetic checkup"))), "$.id");
	}

	private static Map<String, Object> link(String type, String recordId, String documentId) {
		return Map.of("recordType", type, "recordId", recordId, "documentId", documentId);
	}

	@Test
	void aDocumentCanBeAttachedToARecordAndListedWithIt() throws Exception {
		String document = upload(f.profileId);
		String visit = visit();

		MockHttpServletResponse attached = created(f.editor.post(attachments, link("VISIT", visit, document)));
		assertThat(read(attached, "$.document.id")).isEqualTo(document);
		assertThat(read(attached, "$.document.contentType")).isEqualTo("image/png");

		MockHttpServletResponse listed = f.viewer.get(attachments + "?recordType=VISIT&recordId=" + visit);
		assertThat(listed.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(listed.getContentAsString(), "$[*].document.id")).containsExactly(document);
		assertThat(JsonPath.<List<String>>read(f.viewer.get(attachments + "?recordType=VISIT").getContentAsString(),
				"$[*].recordId")).containsExactly(visit);
		assertThat(JsonPath.<List<String>>read(f.viewer.get(attachments + "?recordType=SYMPTOM").getContentAsString(),
				"$")).isEmpty();

		MockHttpServletResponse duplicate = f.editor.post(attachments, link("VISIT", visit, document));
		assertThat(duplicate.getStatus()).isEqualTo(409);
		assertThat(read(duplicate, "$.code")).isEqualTo("ALREADY_ATTACHED");
	}

	@Test
	void everyAttachableRecordTypeWorks() throws Exception {
		String document = upload(f.profileId);
		Map<String, Map.Entry<String, Map<String, Object>>> records = Map.of(
				"CONDITION", Map.entry("conditions", Map.of("name", "Synthetic")),
				"ALLERGY", Map.entry("allergies", Map.of("allergen", "Synthetic")),
				"DIETARY_RESTRICTION", Map.entry("dietary-restrictions", Map.of("kind", "PREFERENCE", "description", "Synthetic")),
				"SYMPTOM", Map.entry("symptoms", Map.of("description", "Synthetic")),
				"MEDICATION", Map.entry("medications", Map.of("name", "Synthetic")),
				"LAB_RESULT", Map.entry("lab-results", Map.of("testName", "Synthetic", "valueText", "Negative")),
				"FOOD_LOG", Map.entry("food-log", Map.of("description", "Synthetic")));
		for (var record : records.entrySet()) {
			String id = read(created(f.editor.post(base + "/" + record.getValue().getKey(), record.getValue().getValue())),
					"$.id");
			created(f.editor.post(attachments, link(record.getKey(), id, document)));
		}
	}

	@Test
	void recordAndDocumentMustBothBelongToTheProfile() throws Exception {
		String document = upload(f.profileId);
		String otherDocument = upload(f.otherProfileId);
		String visit = visit();
		String otherVisit = read(created(f.manager.post("/api/v1/profiles/" + f.otherProfileId + "/visits",
				Map.of("reason", "Synthetic"))), "$.id");

		assertThat(read(f.manager.post(attachments, link("VISIT", otherVisit, document)), "$.code"))
			.isEqualTo("RECORD_NOT_FOUND");
		assertThat(read(f.manager.post(attachments, link("VISIT", visit, otherDocument)), "$.code"))
			.isEqualTo("DOCUMENT_NOT_FOUND");
		// The right ID under the wrong type is not found either.
		assertThat(read(f.manager.post(attachments, link("SYMPTOM", visit, document)), "$.code"))
			.isEqualTo("RECORD_NOT_FOUND");
		assertThat(f.manager.post(attachments, link("EMERGENCY_CONTACT", visit, document)).getStatus())
			.isEqualTo(400);
	}

	@Test
	void accessFollowsTheProfileGrants() throws Exception {
		String document = upload(f.profileId);
		String visit = visit();
		String attachment = read(created(f.editor.post(attachments, link("VISIT", visit, document))), "$.id");

		assertThat(f.viewer.post(attachments, link("VISIT", visit, document)).getStatus()).isEqualTo(403);
		assertThat(f.viewer.delete(attachments + "/" + attachment).getStatus()).isEqualTo(403);
		for (ApiClient noGrant : List.of(f.memberWithoutGrant, f.outsider)) {
			assertThat(noGrant.get(attachments + "?recordType=VISIT").getStatus()).isEqualTo(404);
			assertThat(noGrant.delete(attachments + "/" + attachment).getStatus()).isEqualTo(404);
		}
		assertThat(f.manager.delete("/api/v1/profiles/" + f.otherProfileId + "/attachments/" + attachment).getStatus())
			.isEqualTo(404);
	}

	@Test
	void detachingOrDeletingTheRecordKeepsTheDocument() throws Exception {
		String document = upload(f.profileId);
		String visit = visit();
		String other = visit();
		String attachment = read(created(f.editor.post(attachments, link("VISIT", visit, document))), "$.id");
		created(f.editor.post(attachments, link("VISIT", other, document)));

		assertThat(f.editor.delete(attachments + "/" + attachment).getStatus()).isEqualTo(204);
		assertThat(f.editor.delete(base + "/visits/" + other).getStatus()).isEqualTo(204);

		assertThat(JsonPath.<List<String>>read(f.viewer.get(attachments + "?recordType=VISIT").getContentAsString(),
				"$")).isEmpty();
		assertThat(f.viewer.get(base + "/documents/" + document).getStatus()).isEqualTo(200);
		assertThat(f.editor.delete(attachments + "/" + UUID.randomUUID()).getStatus()).isEqualTo(404);
	}

	@Test
	void deletingTheDocumentRemovesItsAttachmentsButNotTheRecord() throws Exception {
		String document = upload(f.profileId);
		String visit = visit();
		created(f.editor.post(attachments, link("VISIT", visit, document)));

		assertThat(f.manager.delete(base + "/documents/" + document).getStatus()).isEqualTo(204);

		assertThat(JsonPath.<List<String>>read(f.viewer.get(attachments + "?recordType=VISIT").getContentAsString(),
				"$")).isEmpty();
		assertThat(f.viewer.get(base + "/visits/" + visit).getStatus()).isEqualTo(200);
	}

}
