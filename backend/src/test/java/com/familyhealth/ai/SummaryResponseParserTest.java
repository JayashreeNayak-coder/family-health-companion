package com.familyhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familyhealth.ai.AiException.Code;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class SummaryResponseParserTest {

	private static final Set<String> SENT = Set.of("R1", "R2", "R3");

	private final SummaryResponseParser parser = new SummaryResponseParser(JsonMapper.builder().build());

	@Test
	void readsAGroundedSummary() {
		SummaryGenerator.Result result = parser.parse("""
				{"points": [{"topic": "LAB_RESULTS", "text": " HbA1c 5.8 % on 2026-01-10. ", "refs": ["R1", "R1"]}],
				 "gaps": ["The medication has no dosage recorded."],
				 "conflicts": [{"text": "Two different start dates.", "refs": ["R2", "R3"]}]}""", SENT, 10, 20);

		assertThat(result.points()).hasSize(1);
		assertThat(result.points().get(0).text()).isEqualTo("HbA1c 5.8 % on 2026-01-10.");
		assertThat(result.points().get(0).refs()).containsExactly("R1");
		assertThat(result.gaps()).containsExactly("The medication has no dosage recorded.");
		assertThat(result.conflicts().get(0).refs()).containsExactly("R2", "R3");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not json",
			"{\"points\": [], \"gaps\": []}",
			"{\"points\": [], \"gaps\": [], \"conflicts\": [], \"advice\": \"see a doctor\"}",
			"{\"points\": [{\"topic\": \"LAB_RESULTS\", \"text\": \"x\", \"refs\": []}], \"gaps\": [], \"conflicts\": []}",
			"{\"points\": [{\"topic\": \"LAB_RESULTS\", \"text\": \"x\", \"refs\": [\"R9\"]}], \"gaps\": [], \"conflicts\": []}",
			"{\"points\": [{\"topic\": \"DIAGNOSIS\", \"text\": \"x\", \"refs\": [\"R1\"]}], \"gaps\": [], \"conflicts\": []}",
			"{\"points\": [{\"topic\": \"VISITS\", \"text\": \"x\", \"refs\": [\"R1\"], \"risk\": \"high\"}], \"gaps\": [], \"conflicts\": []}",
			"{\"points\": [], \"gaps\": [\"\"], \"conflicts\": []}",
			"{\"points\": [], \"gaps\": [], \"conflicts\": [{\"text\": \"x\", \"refs\": [\"R1\", \"R1\"]}]}" })
	void rejectsUngroundedOrUnexpectedOutput(String output) {
		assertThatThrownBy(() -> parser.parse(output, SENT, null, null)).isInstanceOf(AiException.class)
			.extracting(ex -> ((AiException) ex).code())
			.isEqualTo(Code.MALFORMED_RESPONSE);
	}

}
