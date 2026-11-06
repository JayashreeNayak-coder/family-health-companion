package com.familyhealth.ai;

import com.familyhealth.ai.AiException.Code;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks model output against extraction schema v1 and maps each item to its record's request
 * fields. Anything unexpected rejects the whole response: nothing is repaired by guessing
 * (docs/06 section 8.6). Shared by every provider.
 */
public final class ExtractionResponseParser {

	public static final String VERSION = "v1";

	static final int MAX_ITEMS = 50;

	private static final int MAX_TEXT = 4000;

	private static final Set<String> TOP_LEVEL = Set.of("readable", "items");

	private static final Set<String> DATE_FIELDS = Set.of("startOn", "endOn", "resultOn", "onsetOn", "visitOn");

	/** For each item type, model field to record request field. Fields not listed are dropped. */
	private static final Map<String, Map<String, String>> MAPPINGS = Map.of(
			"MEDICATION", ordered("name", "name", "dosage", "dosageAsRecorded", "instructions",
					"instructionsAsRecorded", "prescribedBy", "prescribedBy", "startOn", "startOn", "endOn", "endOn"),
			"LAB_RESULT", ordered("name", "testName", "valueNumeric", "valueNumeric", "valueText", "valueText", "unit",
					"unit", "resultOn", "resultOn", "referenceRange", "referenceRangeAsPrinted"),
			"CONDITION", ordered("name", "name", "onsetOn", "onsetOn", "notes", "notes"),
			"ALLERGY", ordered("name", "allergen", "reaction", "reactionAsRecorded", "severity", "severityAsRecorded",
					"notes", "notes"),
			"VISIT", ordered("name", "reason", "visitOn", "occurredOn", "doctorName", "doctorName", "facility",
					"facility", "notes", "notes"));

	private static final Set<String> ITEM_FIELDS = Set.of("type", "name", "dosage", "instructions", "prescribedBy",
			"startOn", "endOn", "valueNumeric", "valueText", "unit", "resultOn", "referenceRange", "reaction", "severity",
			"onsetOn", "visitOn", "doctorName", "facility", "notes");

	private final JsonMapper json;

	public ExtractionResponseParser(JsonMapper json) {
		this.json = json;
	}

	/** The record fields an item of this type may carry, in display order. */
	public static List<String> fieldsFor(String type) {
		Map<String, String> mapping = MAPPINGS.get(type);
		return mapping == null ? List.of() : List.copyOf(mapping.values());
	}

	public static Set<String> itemTypes() {
		return MAPPINGS.keySet();
	}

	public DocumentExtractor.Result parse(String text, Integer inputTokens, Integer outputTokens) {
		JsonNode root;
		try {
			root = json.readTree(text);
		}
		catch (JacksonException ex) {
			throw malformed("not JSON");
		}
		if (root == null || !root.isObject() || !TOP_LEVEL.containsAll(root.propertyNames())) {
			throw malformed("unexpected top-level shape");
		}
		JsonNode readable = root.get("readable");
		JsonNode items = root.get("items");
		if (readable == null || !readable.isBoolean() || items == null || !items.isArray()) {
			throw malformed("missing readable or items");
		}
		if (items.size() > MAX_ITEMS) {
			throw malformed("too many items");
		}
		List<DocumentExtractor.Item> parsed = new ArrayList<>();
		for (JsonNode item : items) {
			parsed.add(item(item));
		}
		if (!readable.booleanValue()) {
			parsed = List.of();
		}
		return new DocumentExtractor.Result(readable.booleanValue(), List.copyOf(parsed), inputTokens, outputTokens);
	}

	private DocumentExtractor.Item item(JsonNode item) {
		if (!item.isObject() || !ITEM_FIELDS.containsAll(item.propertyNames())) {
			throw malformed("unexpected item shape");
		}
		JsonNode type = item.get("type");
		if (type == null || !type.isString() || !MAPPINGS.containsKey(type.stringValue())) {
			throw malformed("unknown item type");
		}
		JsonNode name = item.get("name");
		if (name == null || !name.isString() || name.stringValue().isBlank()) {
			throw malformed("item without a name");
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		for (Map.Entry<String, String> field : MAPPINGS.get(type.stringValue()).entrySet()) {
			payload.put(field.getValue(), value(field.getKey(), item.get(field.getKey())));
		}
		return new DocumentExtractor.Item(type.stringValue(), payload);
	}

	private static Object value(String field, JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (field.equals("valueNumeric")) {
			if (!node.isNumber()) {
				throw malformed("valueNumeric is not a number");
			}
			return node.decimalValue();
		}
		if (!node.isString()) {
			throw malformed("a text field is not text");
		}
		String text = node.stringValue().strip();
		if (text.isEmpty()) {
			return null;
		}
		if (text.length() > MAX_TEXT) {
			throw malformed("text too long");
		}
		if (DATE_FIELDS.contains(field)) {
			try {
				LocalDate.parse(text);
			}
			catch (DateTimeParseException ex) {
				throw malformed("a date is not YYYY-MM-DD");
			}
		}
		return text;
	}

	private static AiException malformed(String reason) {
		return new AiException(Code.MALFORMED_RESPONSE, "Model output rejected: " + reason);
	}

	private static Map<String, String> ordered(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

}
