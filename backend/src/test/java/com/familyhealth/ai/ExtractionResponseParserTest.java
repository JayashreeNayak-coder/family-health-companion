package com.familyhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familyhealth.ai.AiException.Code;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExtractionResponseParserTest {

	private final ExtractionResponseParser parser = new ExtractionResponseParser(JsonMapper.builder().build());

	private DocumentExtractor.Result parse(String json) {
		return parser.parse(json, 10, 5);
	}

	private void assertRejected(String json) {
		assertThatThrownBy(() -> parse(json)).isInstanceOf(AiException.class)
			.extracting(ex -> ((AiException) ex).code())
			.isEqualTo(Code.MALFORMED_RESPONSE);
	}

	@Test
	void mapsEachTypeToItsRecordFieldsAndKeepsMissingValuesMissing() {
		DocumentExtractor.Result result = parse("""
				{"readable": true, "items": [
				  {"type": "LAB_RESULT", "name": "HbA1c", "valueNumeric": 5.8, "unit": null, "resultOn": "2026-01-10",
				   "referenceRange": "4.0-5.6", "dosage": "ignored for a lab"},
				  {"type": "MEDICATION", "name": "Synthetic tablet", "dosage": "1 tablet", "instructions": "after food"},
				  {"type": "VISIT", "name": "Follow-up", "visitOn": "2026-02-01", "doctorName": "Dr Synthetic"}
				]}""");

		assertThat(result.readable()).isTrue();
		assertThat(result.inputTokens()).isEqualTo(10);
		assertThat(result.items()).extracting(DocumentExtractor.Item::type)
			.containsExactly("LAB_RESULT", "MEDICATION", "VISIT");
		Map<String, Object> lab = result.items().get(0).payload();
		assertThat(lab).containsEntry("testName", "HbA1c")
			.containsEntry("valueNumeric", new BigDecimal("5.8"))
			.containsEntry("unit", null)
			.containsEntry("resultOn", "2026-01-10")
			.containsEntry("referenceRangeAsPrinted", "4.0-5.6")
			.doesNotContainKey("dosageAsRecorded");
		assertThat(result.items().get(1).payload()).containsEntry("dosageAsRecorded", "1 tablet")
			.containsEntry("startOn", null);
		assertThat(result.items().get(2).payload()).containsEntry("reason", "Follow-up")
			.containsEntry("occurredOn", "2026-02-01");
	}

	@Test
	void anUnreadableDocumentYieldsNoItems() {
		DocumentExtractor.Result result = parse("""
				{"readable": false, "items": [{"type": "CONDITION", "name": "Guess"}]}""");

		assertThat(result.readable()).isFalse();
		assertThat(result.items()).isEmpty();
	}

	@Test
	void anythingOutsideTheSchemaIsRejectedNotRepaired() {
		assertRejected("not json");
		assertRejected("[]");
		assertRejected("{\"readable\": true}");
		assertRejected("{\"readable\": \"yes\", \"items\": []}");
		assertRejected("{\"readable\": true, \"items\": [], \"advice\": \"see a doctor\"}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"DIAGNOSIS\", \"name\": \"x\"}]}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"CONDITION\", \"name\": \" \"}]}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"CONDITION\", \"name\": \"x\", \"risk\": \"high\"}]}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"LAB_RESULT\", \"name\": \"x\", \"valueNumeric\": \"5\"}]}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"CONDITION\", \"name\": \"x\", \"onsetOn\": \"March 2024\"}]}");
		assertRejected("{\"readable\": true, \"items\": [{\"type\": \"CONDITION\", \"name\": \"x\", \"onsetOn\": \"2024-02-30\"}]}");
		assertRejected("{\"readable\": true, \"items\": [" + "{\"type\": \"CONDITION\", \"name\": \"x\"},".repeat(50)
				+ "{\"type\": \"CONDITION\", \"name\": \"x\"}]}");
	}

	@Test
	void fieldsForEachTypeAreKnown() {
		assertThat(ExtractionResponseParser.fieldsFor("ALLERGY")).containsExactly("allergen", "reactionAsRecorded",
				"severityAsRecorded", "notes");
		assertThat(ExtractionResponseParser.fieldsFor("UNKNOWN")).isEmpty();
	}

}
