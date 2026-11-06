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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class LabResultIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	private ProfileFixture f;

	private String labs;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		labs = "/api/v1/profiles/" + f.profileId + "/lab-results";
	}

	private void add(String name, Object value, String unit, String date) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("testName", name);
		if (value instanceof String text) {
			body.put("valueText", text);
		}
		else {
			body.put("valueNumeric", value);
		}
		body.put("unit", unit);
		body.put("resultOn", date);
		created(f.editor.post(labs, body));
	}

	@Test
	void missingUnitAndDateStayMissingAndTheNameIsGroupedLoosely() throws Exception {
		MockHttpServletResponse response = created(f.editor.post(labs,
				Map.of("testName", "  Fasting   Glucose ", "valueNumeric", 92, "referenceRangeAsPrinted", "70-100")));

		assertThat(read(response, "$.testName")).isEqualTo("Fasting   Glucose");
		assertThat(read(response, "$.testKey")).isEqualTo("fasting glucose");
		assertThat(JsonPath.<Object>read(response.getContentAsString(), "$.unit")).isNull();
		assertThat(JsonPath.<Object>read(response.getContentAsString(), "$.resultOn")).isNull();
		assertThat(read(response, "$.sourceType")).isEqualTo("USER_REPORTED");
		assertThat(response.getContentAsString()).doesNotContainIgnoringCase("normal");
	}

	@Test
	void aValueIsRequired() throws Exception {
		assertThat(f.editor.post(labs, Map.of("testName", "Synthetic", "valueText", " ")).getStatus()).isEqualTo(400);
	}

	@Test
	void aTrendHoldsOnlyComparableValuesAndCountsTheRest() throws Exception {
		add("HbA1c", 6.1, "%", "2026-03-01");
		add("hba1c", 5.8, "%", "2026-01-10");
		add("HbA1c", 43, "mmol/mol", "2026-02-01");
		add("HbA1c", 5.9, "%", null);
		add("HbA1c", "Pending", "%", "2026-04-01");
		add("Glucose", 90, "mg/dL", "2026-01-10");

		MockHttpServletResponse trend = f.viewer.get(labs + "/trends?testKey=HbA1c&unit=%");

		assertThat(trend.getStatus()).isEqualTo(200);
		String body = trend.getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.points[*].resultOn")).containsExactly("2026-01-10", "2026-03-01");
		assertThat(JsonPath.<List<Double>>read(body, "$.points[*].value")).containsExactly(5.8, 6.1);
		assertThat(JsonPath.<Integer>read(body, "$.excludedCount")).isEqualTo(3);
		assertThat(body).doesNotContainIgnoringCase("normal").doesNotContainIgnoringCase("high");

		MockHttpServletResponse series = f.viewer.get(labs + "/trend-series");
		assertThat(JsonPath.<List<String>>read(series.getContentAsString(), "$[*].unit"))
			.containsExactly("mg/dL", "%", "mmol/mol");
		assertThat(JsonPath.<List<Integer>>read(series.getContentAsString(), "$[*].count")).containsExactly(1, 2, 1);
	}

	@Test
	void valuesWithoutAUnitFormTheirOwnTrend() throws Exception {
		add("Urine pH", 6, null, "2026-01-01");
		add("Urine pH", 6.5, null, "2026-02-01");

		String body = f.viewer.get(labs + "/trends?testKey=Urine pH").getContentAsString();
		assertThat(JsonPath.<List<Object>>read(body, "$.points")).hasSize(2);
		assertThat(JsonPath.<Object>read(body, "$.unit")).isNull();
	}

	@Test
	void trendsFollowTheProfileGrants() throws Exception {
		assertThat(f.outsider.get(labs + "/trends?testKey=x").getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(labs + "/trend-series").getStatus()).isEqualTo(404);
		assertThat(f.signedOut.get(labs + "/trend-series").getStatus()).isEqualTo(401);
	}

	@Test
	void labResultsAppearOnTheTimeline() throws Exception {
		add("Synthetic TSH", 2.1, "mIU/L", "2026-05-01");

		String body = f.viewer.get("/api/v1/profiles/" + f.profileId + "/timeline?types=LAB_RESULT").getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.entries[*].title")).containsExactly("Synthetic TSH");
		assertThat(JsonPath.<List<String>>read(body, "$.entries[*].detail")).containsExactly("2.1 mIU/L");
	}

}
