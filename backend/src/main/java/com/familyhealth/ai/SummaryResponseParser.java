package com.familyhealth.ai;

import com.familyhealth.ai.AiException.Code;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks model output against summary schema v1 and its grounding: every point and conflict must
 * cite records that were sent (docs/06 section 8.3). Anything else rejects the whole response.
 */
public final class SummaryResponseParser {

	public static final String VERSION = "v1";

	static final int MAX_POINTS = 60;

	static final int MAX_GAPS = 20;

	static final int MAX_CONFLICTS = 20;

	static final int MAX_REFS = 30;

	private static final int MAX_TEXT = 600;

	private static final Set<String> TOP_LEVEL = Set.of("points", "gaps", "conflicts");

	private static final Set<String> POINT_FIELDS = Set.of("topic", "text", "refs");

	private static final Set<String> CONFLICT_FIELDS = Set.of("text", "refs");

	private final JsonMapper json;

	public SummaryResponseParser(JsonMapper json) {
		this.json = json;
	}

	/** {@code sentRefs} are the references of the records that were sent. */
	public SummaryGenerator.Result parse(String text, Set<String> sentRefs, Integer inputTokens, Integer outputTokens) {
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
		JsonNode points = array(root.get("points"), MAX_POINTS);
		JsonNode gaps = array(root.get("gaps"), MAX_GAPS);
		JsonNode conflicts = array(root.get("conflicts"), MAX_CONFLICTS);

		List<SummaryGenerator.Point> parsedPoints = new ArrayList<>();
		for (JsonNode point : points) {
			if (!point.isObject() || !POINT_FIELDS.containsAll(point.propertyNames())) {
				throw malformed("unexpected point fields");
			}
			String topic = text(point.get("topic"), 40);
			if (!SummaryGenerator.TOPICS.contains(topic)) {
				throw malformed("unknown topic");
			}
			parsedPoints.add(new SummaryGenerator.Point(topic, text(point.get("text"), MAX_TEXT),
					refs(point.get("refs"), 1, sentRefs)));
		}
		List<String> parsedGaps = new ArrayList<>();
		for (JsonNode gap : gaps) {
			parsedGaps.add(text(gap, MAX_TEXT));
		}
		List<SummaryGenerator.Conflict> parsedConflicts = new ArrayList<>();
		for (JsonNode conflict : conflicts) {
			if (!conflict.isObject() || !CONFLICT_FIELDS.containsAll(conflict.propertyNames())) {
				throw malformed("unexpected conflict fields");
			}
			parsedConflicts.add(new SummaryGenerator.Conflict(text(conflict.get("text"), MAX_TEXT),
					refs(conflict.get("refs"), 2, sentRefs)));
		}
		return new SummaryGenerator.Result(List.copyOf(parsedPoints), List.copyOf(parsedGaps),
				List.copyOf(parsedConflicts), inputTokens, outputTokens);
	}

	private static JsonNode array(JsonNode node, int max) {
		if (node == null || !node.isArray() || node.size() > max) {
			throw malformed("expected a list of at most " + max);
		}
		return node;
	}

	private static String text(JsonNode node, int max) {
		if (node == null || !node.isString()) {
			throw malformed("expected text");
		}
		String value = node.asString().strip();
		if (value.isEmpty() || value.length() > max) {
			throw malformed("text empty or too long");
		}
		return value;
	}

	/** At least {@code min} distinct references, each one of the records sent. */
	private static List<String> refs(JsonNode node, int min, Set<String> sentRefs) {
		if (node == null || !node.isArray() || node.size() > MAX_REFS) {
			throw malformed("expected references");
		}
		Set<String> refs = new LinkedHashSet<>();
		for (JsonNode ref : node) {
			if (!ref.isString() || !sentRefs.contains(ref.asString())) {
				throw malformed("cites a record that was not sent");
			}
			refs.add(ref.asString());
		}
		if (refs.size() < min) {
			throw malformed("too few references");
		}
		return List.copyOf(refs);
	}

	private static AiException malformed(String reason) {
		return new AiException(Code.MALFORMED_RESPONSE, "Summary output rejected: " + reason);
	}

}
