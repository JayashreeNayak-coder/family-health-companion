package com.familyhealth.summary;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.FakeSummaryGenerator;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.familyhealth.ai.AiException;
import com.familyhealth.ai.SummaryGenerator;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class SummaryIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeSummaryGenerator fake;

	private ProfileFixture f;

	private String base;

	private String summaries;

	@BeforeEach
	void setUp() throws Exception {
		fake.reset();
		f = new ProfileFixture(mockMvc);
		base = "/api/v1/profiles/" + f.profileId;
		summaries = base + "/summaries";
	}

	private void seedRecords() throws Exception {
		f.editor.post(base + "/allergies",
				Map.of("allergen", "Sesame", "reactionAsRecorded", "Hives", "notes", "Private allergy note"));
		f.editor.post(base + "/conditions", Map.of("name", "Synthetic condition", "status", "ACTIVE", "notes",
				"Private condition note"));
		f.editor.post(base + "/lab-results", Map.of("testName", "Synthetic HbA1c", "valueNumeric", 5.8, "unit", "%",
				"resultOn", "2026-01-10"));
		f.editor.post(base + "/medications",
				Map.of("name", "Old synthetic medicine", "dosageAsRecorded", "1 tablet", "startOn", "2025-06-01"));
	}

	private static SummaryGenerator.Result answer() {
		return new SummaryGenerator.Result(
				List.of(new SummaryGenerator.Point("ALLERGIES", "Allergy to Sesame, reaction Hives.", List.of("R1")),
						new SummaryGenerator.Point("LAB_RESULTS", "Synthetic HbA1c 5.8 % on 2026-01-10.", List.of("R3"))),
				List.of("The condition has no onset date."), List.of(), 300, 120);
	}

	private long count() {
		return jdbc.queryForObject("select count(*) from health_summary where profile_id = ?::uuid", Long.class,
				f.profileId);
	}

	@Test
	void sendsOnlyTheRecordsInThePeriodAndReturnsACitedLabelledSummary() throws Exception {
		seedRecords();
		fake.willAnswer(answer());

		MockHttpServletResponse response = f.viewer.post(summaries,
				Map.of("periodFrom", "2026-01-01", "periodTo", "2026-12-31"));

		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		SummaryGenerator.Request sent = fake.requests().get(0);
		assertThat(sent.from()).isEqualTo("2026-01-01");
		assertThat(sent.records()).extracting(SummaryGenerator.Input::ref).containsExactly("R1", "R2", "R3");
		assertThat(sent.records()).extracting(SummaryGenerator.Input::type)
			.containsExactly("ALLERGY", "CONDITION", "LAB_RESULT");
		assertThat(sent.records().get(1).date()).isNull();
		assertThat(sent.toString()).doesNotContain("Private").doesNotContain("Old synthetic medicine")
			.doesNotContain("Child Synthetic").doesNotContain(f.profileId);

		String body = response.getContentAsString();
		assertThat(JsonPath.<String>read(body, "$.contentType")).isEqualTo("AI_GENERATED");
		assertThat(JsonPath.<String>read(body, "$.disclaimer")).contains("not medical advice");
		assertThat(JsonPath.<String>read(body, "$.points[1].sources[0].title")).isEqualTo("Synthetic HbA1c");
		assertThat(JsonPath.<String>read(body, "$.points[1].sources[0].sourceType")).isEqualTo("USER_REPORTED");
		assertThat(JsonPath.<String>read(body, "$.points[1].sources[0].date")).isEqualTo("2026-01-10");
		assertThat(JsonPath.<List<String>>read(body, "$.gaps")).containsExactly("The condition has no onset date.");
		assertThat(JsonPath.<List<String>>read(body, "$.limitations"))
			.anyMatch(l -> l.equals("No medications are recorded in this period."))
			.anyMatch(l -> l.equals("No visits are recorded in this period."))
			.anyMatch(l -> l.contains("marked as date unknown"))
			.noneMatch(l -> l.contains("No allergies"));

		// Stored, and readable later by anyone with VIEW.
		String id = JsonPath.read(body, "$.id");
		MockHttpServletResponse again = f.viewer.get(summaries + "/" + id);
		assertThat(again.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(again.getContentAsString(), "$.points[0].text"))
			.isEqualTo("Allergy to Sesame, reaction Hives.");
		assertThat(JsonPath.<Integer>read(f.viewer.get(summaries).getContentAsString(), "$.totalItems")).isEqualTo(1);
	}

	@Test
	void nothingToSummariseNeverReachesTheProvider() throws Exception {
		MockHttpServletResponse empty = f.viewer.post(summaries, Map.of());

		assertThat(empty.getStatus()).isEqualTo(422);
		assertThat(read(empty, "$.code")).isEqualTo("NO_RECORDS");
		assertThat(fake.requests()).isEmpty();
	}

	@Test
	void aRejectedAnswerStoresNothing() throws Exception {
		seedRecords();
		fake.willAnswer(new AiException(AiException.Code.MALFORMED_RESPONSE, "cites R9"));

		MockHttpServletResponse rejected = f.viewer.post(summaries, Map.of());

		assertThat(rejected.getStatus()).isEqualTo(502);
		assertThat(count()).isZero();
	}

	@Test
	void anInvalidPeriodIsRejected() throws Exception {
		MockHttpServletResponse invalid = f.viewer.post(summaries,
				Map.of("periodFrom", "2026-02-01", "periodTo", "2026-01-01"));
		assertThat(invalid.getStatus()).isEqualTo(400);
		assertThat(fake.requests()).isEmpty();
	}

	@Test
	void accessFollowsTheProfileGrants() throws Exception {
		seedRecords();
		assertThat(f.signedOut.post(summaries, Map.of()).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(summaries, Map.of()).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.post(summaries, Map.of()).getStatus()).isEqualTo(404);
		fake.setAvailable(false);
		assertThat(f.outsider.post(summaries, Map.of()).getStatus()).isEqualTo(404);
		assertThat(f.viewer.post(summaries, Map.of()).getStatus()).isEqualTo(503);
		fake.setAvailable(true);
		assertThat(fake.requests()).isEmpty();

		fake.willAnswer(answer());
		String id = read(created(f.viewer.post(summaries, Map.of())), "$.id");
		String one = summaries + "/" + id;
		assertThat(f.outsider.get(one).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(summaries).getStatus()).isEqualTo(404);
		assertThat(f.manager.get("/api/v1/profiles/" + f.otherProfileId + "/summaries/" + id).getStatus())
			.isEqualTo(404);
		assertThat(f.viewer.delete(one).getStatus()).isEqualTo(403);
		assertThat(f.editor.delete(one).getStatus()).isEqualTo(204);
		assertThat(count()).isZero();
	}

}
