package com.familyhealth.records.timeline;

import static com.familyhealth.ProfileFixture.created;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class TimelineIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	private ProfileFixture f;

	private String base;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		base = "/api/v1/profiles/" + f.profileId;
	}

	private void add(String resource, Map<String, ?> body) throws Exception {
		created(f.editor.post(base + "/" + resource, body));
	}

	private void addOneOfEach() throws Exception {
		add("symptoms", Map.of("description", "Afternoon symptom", "occurredOn", "2026-03-10", "occurredTime", "15:00"));
		add("symptoms", Map.of("description", "Morning symptom", "occurredOn", "2026-03-10", "occurredTime", "08:00"));
		add("symptoms", Map.of("description", "Untimed symptom", "occurredOn", "2026-03-10"));
		add("visits", Map.of("reason", "Newest visit", "occurredOn", "2026-04-01", "doctorName", "Dr. Synthetic"));
		add("conditions", Map.of("name", "Oldest condition", "onsetOn", "2020-01-01"));
		add("visits", Map.of("reason", "Undated visit"));
		add("conditions", Map.of("name", "Undated condition"));
	}

	private List<String> titles(String query, String group) throws Exception {
		return JsonPath.read(f.viewer.get(base + "/timeline" + query).getContentAsString(), "$." + group + "[*].title");
	}

	@Test
	void ordersNewestFirstWithAllTypesAndKeepsUndatedRecordsApart() throws Exception {
		addOneOfEach();

		assertThat(titles("", "entries")).containsExactly("Newest visit", "Afternoon symptom", "Morning symptom",
				"Untimed symptom", "Oldest condition");
		assertThat(titles("", "dateUnknown")).containsExactly("Undated condition", "Undated visit");
	}

	@Test
	void anUndatedRecordHasNoSubstitutedDate() throws Exception {
		add("visits", Map.of("reason", "Undated visit"));

		String body = f.viewer.get(base + "/timeline").getContentAsString();

		assertThat(JsonPath.<Object>read(body, "$.dateUnknown[0].date")).isNull();
		assertThat(JsonPath.<String>read(body, "$.dateUnknown[0].sourceType")).isEqualTo("USER_REPORTED");
	}

	@Test
	void timesUseTheDocumentedFormat() throws Exception {
		add("symptoms", Map.of("description", "Timed", "occurredOn", "2026-03-10", "occurredTime", "08:30"));

		String body = f.viewer.get(base + "/timeline").getContentAsString();

		assertThat(JsonPath.<String>read(body, "$.entries[0].time")).isEqualTo("08:30");
	}

	@Test
	void filtersByDateRangeAndTypeWithoutHidingUndatedRecordsFromARange() throws Exception {
		addOneOfEach();

		assertThat(titles("?from=2026-03-11", "entries")).containsExactly("Newest visit");
		assertThat(titles("?to=2021-01-01", "entries")).containsExactly("Oldest condition");
		assertThat(titles("?from=2026-03-11", "dateUnknown")).hasSize(2);

		assertThat(titles("?types=VISIT", "entries")).containsExactly("Newest visit");
		assertThat(titles("?types=VISIT", "dateUnknown")).containsExactly("Undated visit");
		assertThat(titles("?types=VISIT,CONDITION", "entries")).containsExactly("Newest visit", "Oldest condition");
	}

	@Test
	void rejectsInvalidFilters() throws Exception {
		assertThat(f.viewer.get(base + "/timeline?from=2026-02-01&to=2026-01-01").getStatus()).isEqualTo(400);
		assertThat(f.viewer.get(base + "/timeline?types=NOPE").getStatus()).isEqualTo(400);
		assertThat(f.viewer.get(base + "/timeline?from=yesterday").getStatus()).isEqualTo(400);
	}

	@Test
	void authorizationMatrix() throws Exception {
		add("symptoms", Map.of("description", "Private symptom", "occurredOn", "2026-03-10"));
		String path = base + "/timeline";

		assertThat(f.signedOut.get(path).getStatus()).isEqualTo(401);
		assertThat(f.outsider.get(path).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(path).getStatus()).isEqualTo(404);
		assertThat(f.viewer.get(path).getStatus()).isEqualTo(200);
	}

	@Test
	void showsOnlyThisProfilesRecords() throws Exception {
		add("symptoms", Map.of("description", "Private symptom", "occurredOn", "2026-03-10"));
		f.grant(f.otherProfileId, f.viewerId, "VIEW");

		String other = f.viewer.get("/api/v1/profiles/" + f.otherProfileId + "/timeline").getContentAsString();

		assertThat(other).doesNotContain("Private symptom");
	}

}
