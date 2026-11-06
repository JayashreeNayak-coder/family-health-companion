package com.familyhealth.extraction;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.FakeDocumentExtractor;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.familyhealth.ai.AiException;
import com.familyhealth.ai.DocumentExtractor;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class ExtractionIntegrationTest extends IntegrationTestBase {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 9 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeDocumentExtractor fake;

	@Autowired
	private ExtractionWorker worker;

	private ProfileFixture f;

	private String base;

	private String document;

	private String docPath;

	@BeforeEach
	void setUp() throws Exception {
		fake.reset();
		// Other tests' queued jobs mustn't be picked up here.
		jdbc.update("update extraction_job set status = 'FAILED' where status in ('QUEUED', 'PROCESSING')");
		f = new ProfileFixture(mockMvc);
		base = "/api/v1/profiles/" + f.profileId;
		document = read(created(f.editor.upload(base + "/documents", "prescription.png", "image/png", PNG,
				Map.of("category", "PRESCRIPTION"))), "$.id");
		docPath = base + "/documents/" + document;
	}

	private static DocumentExtractor.Result result(DocumentExtractor.Item... items) {
		return new DocumentExtractor.Result(true, List.of(items), 100, 20);
	}

	private static DocumentExtractor.Item item(String type, Object... fields) {
		Map<String, Object> payload = new LinkedHashMap<>();
		for (int i = 0; i < fields.length; i += 2) {
			payload.put((String) fields[i], fields[i + 1]);
		}
		return new DocumentExtractor.Item(type, payload);
	}

	private String extractAndReview(DocumentExtractor.Item... items) throws Exception {
		fake.willAnswer(result(items));
		assertThat(f.editor.post(docPath + "/extraction", Map.of()).getStatus()).isEqualTo(202);
		assertThat(worker.runOnce()).isTrue();
		return f.editor.get(docPath + "/draft").getContentAsString();
	}

	private String documentStatus() throws Exception {
		return read(f.viewer.get(docPath), "$.extractionStatus");
	}

	private long count(String table) {
		return jdbc.queryForObject("select count(*) from " + table + " where profile_id = ?::uuid", Long.class,
				f.profileId);
	}

	private static Map<String, Object> body(Object... pairs) {
		Map<String, Object> body = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			body.put((String) pairs[i], pairs[i + 1]);
		}
		return body;
	}

	@Test
	void aDraftIsReviewedBeforeAnyRecordExists() throws Exception {
		MockHttpServletResponse requested = f.editor.post(docPath + "/extraction", Map.of());
		assertThat(requested.getStatus()).isEqualTo(202);
		assertThat(read(requested, "$.status")).isEqualTo("QUEUED");
		assertThat(documentStatus()).isEqualTo("QUEUED");

		fake.willAnswer(result(item("MEDICATION", "name", "Synthetic tablet", "dosageAsRecorded", "1 tablet",
				"instructionsAsRecorded", null, "prescribedBy", null, "startOn", null, "endOn", null),
				item("LAB_RESULT", "testName", "HbA1c", "valueNumeric", new BigDecimal("5.8"), "valueText", null, "unit",
						null, "resultOn", "2026-01-10", "referenceRangeAsPrinted", null)));
		assertThat(worker.runOnce()).isTrue();
		assertThat(worker.runOnce()).isFalse();

		assertThat(documentStatus()).isEqualTo("READY_FOR_REVIEW");
		assertThat(read(f.viewer.get(docPath + "/extraction"), "$.status")).isEqualTo("SUCCEEDED");
		String draft = f.editor.get(docPath + "/draft").getContentAsString();
		assertThat(JsonPath.<String>read(draft, "$.contentType")).isEqualTo("AI_GENERATED");
		assertThat(JsonPath.<String>read(draft, "$.disclaimer")).contains("Nothing is saved until you approve it");
		assertThat(JsonPath.<List<String>>read(draft, "$.items[*].type")).containsExactly("MEDICATION", "LAB_RESULT");
		assertThat(JsonPath.<Object>read(draft, "$.items[1].payload.unit")).isNull();
		assertThat(count("medication")).isZero();
		assertThat(count("lab_result")).isZero();
	}

	@Test
	void approvingWithCorrectionsCreatesADocumentedRecordWithItsSourceAttached() throws Exception {
		String draft = extractAndReview(item("MEDICATION", "name", "Synthetic tabelt", "dosageAsRecorded", "1 tablet",
				"instructionsAsRecorded", null, "prescribedBy", null, "startOn", null, "endOn", null));
		String itemId = JsonPath.read(draft, "$.items[0].id");
		int version = JsonPath.read(draft, "$.items[0].version");

		MockHttpServletResponse approved = f.editor.post(docPath + "/draft/items/" + itemId + "/approve",
				body("version", version, "payload", body("name", "Synthetic tablet", "dosageAsRecorded", "1 tablet",
						"startOn", "2026-01-01")));

		assertThat(approved.getStatus()).as(approved.getContentAsString()).isEqualTo(200);
		assertThat(read(approved, "$.status")).isEqualTo("APPROVED");
		String recordId = read(approved, "$.confirmedRecordId");
		MockHttpServletResponse record = f.viewer.get(base + "/medications/" + recordId);
		assertThat(read(record, "$.name")).isEqualTo("Synthetic tablet");
		assertThat(read(record, "$.startOn")).isEqualTo("2026-01-01");
		assertThat(read(record, "$.sourceType")).isEqualTo("DOCUMENTED");
		assertThat(read(record, "$.sourceDocumentId")).isEqualTo(document);
		assertThat(jdbc.queryForObject("select extraction_draft_item_id::text from medication where id = ?::uuid",
				String.class, recordId)).isEqualTo(itemId);
		assertThat(JsonPath.<List<String>>read(
				f.viewer.get(base + "/attachments?recordType=MEDICATION&recordId=" + recordId).getContentAsString(),
				"$[*].document.id")).containsExactly(document);
		assertThat(documentStatus()).isEqualTo("REVIEWED");
		assertThat(read(f.editor.get(docPath + "/draft"), "$.status")).isEqualTo("COMPLETED");

		MockHttpServletResponse again = f.editor.post(docPath + "/draft/items/" + itemId + "/approve",
				body("version", version + 1));
		assertThat(again.getStatus()).isEqualTo(409);
		assertThat(count("medication")).isEqualTo(1);
		assertThat(jdbc.queryForList("select action from audit_event where profile_id = ?::uuid", String.class,
				f.profileId)).contains("EXTRACTION_REQUESTED", "DRAFT_ITEM_APPROVED");
	}

	@Test
	void rejectingCreatesNothing() throws Exception {
		String draft = extractAndReview(item("CONDITION", "name", "Synthetic", "onsetOn", null, "notes", null),
				item("ALLERGY", "allergen", "Synthetic pollen", "reactionAsRecorded", null, "severityAsRecorded", null,
						"notes", null));
		String first = JsonPath.read(draft, "$.items[0].id");

		assertThat(f.editor.post(docPath + "/draft/items/" + first + "/reject", body("version", 0)).getStatus())
			.isEqualTo(200);
		assertThat(documentStatus()).isEqualTo("READY_FOR_REVIEW");
		MockHttpServletResponse rest = f.editor.post(docPath + "/draft/reject", Map.of());

		assertThat(read(rest, "$.status")).isEqualTo("REJECTED");
		assertThat(JsonPath.<List<String>>read(rest.getContentAsString(), "$.items[*].status"))
			.containsExactly("REJECTED", "REJECTED");
		assertThat(count("condition")).isZero();
		assertThat(count("allergy")).isZero();
		assertThat(documentStatus()).isEqualTo("REVIEWED");
	}

	@Test
	void invalidCorrectionsAreRejectedAndTheItemStaysPending() throws Exception {
		String draft = extractAndReview(item("VISIT", "reason", "Checkup", "occurredOn", null, "doctorName", null,
				"facility", null, "notes", null));
		String itemId = JsonPath.read(draft, "$.items[0].id");
		String approve = docPath + "/draft/items/" + itemId + "/approve";

		MockHttpServletResponse badDate = f.editor.post(approve,
				body("version", 0, "payload", body("reason", "Checkup", "occurredOn", "yesterday")));
		assertThat(badDate.getStatus()).isEqualTo(400);
		assertThat(read(badDate, "$.code")).isEqualTo("VALIDATION_FAILED");
		assertThat(read(badDate, "$.errors[0].field")).isEqualTo("occurredOn");

		MockHttpServletResponse unknownField = f.editor.post(approve,
				body("version", 0, "payload", body("reason", "Checkup", "diagnosis", "x")));
		assertThat(read(unknownField, "$.errors[0].field")).isEqualTo("diagnosis");

		MockHttpServletResponse corrected = f.editor.patch(docPath + "/draft/items/" + itemId,
				body("version", 0, "payload", body("reason", "  ", "facility", "Synthetic Clinic")));
		assertThat(corrected.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<Object>read(corrected.getContentAsString(), "$.payload.reason")).isNull();
		assertThat(read(corrected, "$.status")).isEqualTo("PENDING");
		assertThat(count("doctor_visit")).isZero();

		MockHttpServletResponse stale = f.editor.post(approve, body("version", 0));
		assertThat(stale.getStatus()).isEqualTo(409);
	}

	@Test
	void failuresKeepTheOriginalAndCreateNoDraft() throws Exception {
		fake.willAnswer(new AiException(AiException.Code.TIMEOUT, "slow"),
				new AiException(AiException.Code.UNAVAILABLE, "down"));
		f.editor.post(docPath + "/extraction", Map.of());

		assertThat(worker.runOnce()).isTrue();
		assertThat(documentStatus()).isEqualTo("QUEUED");
		assertThat(worker.runOnce()).isTrue();
		assertThat(worker.runOnce()).isFalse();

		assertThat(fake.calls()).isEqualTo(2);
		MockHttpServletResponse job = f.viewer.get(docPath + "/extraction");
		assertThat(read(job, "$.status")).isEqualTo("FAILED");
		assertThat(read(job, "$.errorCode")).isEqualTo("UNAVAILABLE");
		assertThat(documentStatus()).isEqualTo("FAILED");
		assertThat(f.editor.get(docPath + "/draft").getStatus()).isEqualTo(404);
		assertThat(f.viewer.get(docPath + "/content").getContentAsByteArray()).isEqualTo(PNG);

		fake.willAnswer(new AiException(AiException.Code.MALFORMED_RESPONSE, "bad"));
		assertThat(f.editor.post(docPath + "/extraction", Map.of()).getStatus()).isEqualTo(202);
		worker.runOnce();
		assertThat(read(f.viewer.get(docPath + "/extraction"), "$.errorCode")).isEqualTo("MALFORMED_RESPONSE");
		assertThat(fake.calls()).isEqualTo(3);
	}

	@Test
	void anUnreadableDocumentProducesNoDraft() throws Exception {
		fake.willAnswer(new DocumentExtractor.Result(false, List.of(), 50, 5));
		f.editor.post(docPath + "/extraction", Map.of());
		worker.runOnce();

		assertThat(read(f.viewer.get(docPath + "/extraction"), "$.errorCode")).isEqualTo("UNREADABLE");
		assertThat(documentStatus()).isEqualTo("FAILED");
	}

	@Test
	void unavailableBusyAndOverLimitRequestsAreRefused() throws Exception {
		fake.setAvailable(false);
		MockHttpServletResponse unavailable = f.editor.post(docPath + "/extraction", Map.of());
		assertThat(unavailable.getStatus()).isEqualTo(503);
		assertThat(read(unavailable, "$.code")).isEqualTo("AI_UNAVAILABLE");
		fake.setAvailable(true);

		assertThat(f.editor.post(docPath + "/extraction", Map.of()).getStatus()).isEqualTo(202);
		assertThat(read(f.editor.post(docPath + "/extraction", Map.of()), "$.code")).isEqualTo("EXTRACTION_IN_PROGRESS");

		fake.willAnswer(result(item("CONDITION", "name", "Synthetic", "onsetOn", null, "notes", null)));
		worker.runOnce();
		assertThat(read(f.editor.post(docPath + "/extraction", Map.of()), "$.code")).isEqualTo("DRAFT_PENDING_REVIEW");

		jdbc.update("""
				insert into ai_usage_event (id, user_account_id, feature, occurred_at, outcome)
				select gen_random_uuid(), ?::uuid, 'DOCUMENT_EXTRACTION', now(), 'REQUESTED' from generate_series(1, 25)
				""", f.managerId);
		String other = read(created(f.manager.upload(base + "/documents", "b.png", "image/png", PNG, Map.of())), "$.id");
		MockHttpServletResponse limited = f.manager.post(base + "/documents/" + other + "/extraction", Map.of());
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(read(limited, "$.code")).isEqualTo("AI_LIMIT_REACHED");
	}

	@Test
	void accessFollowsTheProfileGrants() throws Exception {
		assertThat(f.viewer.post(docPath + "/extraction", Map.of()).getStatus()).isEqualTo(403);
		String draft = extractAndReview(item("CONDITION", "name", "Synthetic", "onsetOn", null, "notes", null));
		String itemId = JsonPath.read(draft, "$.items[0].id");

		assertThat(f.viewer.get(docPath + "/extraction").getStatus()).isEqualTo(200);
		assertThat(f.viewer.get(docPath + "/draft").getStatus()).isEqualTo(403);
		assertThat(f.viewer.post(docPath + "/draft/items/" + itemId + "/approve", body("version", 0)).getStatus())
			.isEqualTo(403);
		for (ApiClient noGrant : List.of(f.memberWithoutGrant, f.outsider)) {
			assertThat(noGrant.get(docPath + "/draft").getStatus()).isEqualTo(404);
			assertThat(noGrant.post(docPath + "/draft/items/" + itemId + "/approve", body("version", 0)).getStatus())
				.isEqualTo(404);
		}
		// The right document under another profile the manager can open is still not found.
		String otherDoc = "/api/v1/profiles/" + f.otherProfileId + "/documents/" + document;
		assertThat(f.manager.get(otherDoc + "/draft").getStatus()).isEqualTo(404);
		assertThat(f.manager.post(otherDoc + "/draft/items/" + itemId + "/approve", body("version", 0)).getStatus())
			.isEqualTo(404);
		assertThat(count("condition")).isZero();
	}

	@Test
	void deletingTheDocumentKeepsApprovedRecords() throws Exception {
		String draft = extractAndReview(item("CONDITION", "name", "Synthetic", "onsetOn", null, "notes", null));
		String itemId = JsonPath.read(draft, "$.items[0].id");
		String recordId = read(f.editor.post(docPath + "/draft/items/" + itemId + "/approve", body("version", 0)),
				"$.confirmedRecordId");

		assertThat(f.manager.delete(docPath).getStatus()).isEqualTo(204);

		MockHttpServletResponse record = f.viewer.get(base + "/conditions/" + recordId);
		assertThat(read(record, "$.name")).isEqualTo("Synthetic");
		assertThat(JsonPath.<Object>read(record.getContentAsString(), "$.sourceDocumentDeletedAt")).isNotNull();
		assertThat(jdbc.queryForObject("select extraction_draft_item_id from condition where id = ?::uuid",
				String.class, recordId)).isNull();
	}

}
