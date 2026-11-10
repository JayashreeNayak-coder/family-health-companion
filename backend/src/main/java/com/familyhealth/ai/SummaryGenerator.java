package com.familyhealth.ai;

import java.util.List;

/**
 * Restates a profile's records as a short, cited summary. Implementations send only the records
 * given, each under a short reference such as "R1" instead of its database ID, and give the model
 * no tools (docs/06 sections 8.2 and 8.3).
 */
public interface SummaryGenerator {

	/** The summary topics, in display order. */
	List<String> TOPICS = List.of("CONDITIONS", "ALLERGIES", "MEDICATIONS", "LAB_RESULTS", "VISITS", "SYMPTOMS");

	boolean isAvailable();

	String provider();

	String model();

	String promptVersion();

	/**
	 * @throws AiException when the provider fails, or its output doesn't match the schema or cites
	 * a reference that wasn't sent
	 */
	Result generate(Request request);

	/**
	 * One record as sent. {@code date} is YYYY-MM-DD or null when unknown. {@code source} is
	 * USER_REPORTED or DOCUMENTED. {@code detail} may be null.
	 */
	record Input(String ref, String type, String date, String title, String detail, String source) {
	}

	/** {@code from} and {@code to} are YYYY-MM-DD or null for no limit. */
	record Request(String from, String to, List<Input> records) {
	}

	/** One statement, citing at least one record it restates. */
	record Point(String topic, String text, List<String> refs) {
	}

	/** Two or more records that disagree, cited. */
	record Conflict(String text, List<String> refs) {
	}

	record Result(List<Point> points, List<String> gaps, List<Conflict> conflicts, Integer inputTokens,
			Integer outputTokens) {
	}

}
