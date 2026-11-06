package com.familyhealth.ai;

import java.util.List;
import java.util.Map;

/**
 * Reads one document and returns what it states, as draft items. Implementations send the
 * document only, as data separate from the instructions, and give the model no tools
 * (docs/06 sections 8.2 and 8.4).
 */
public interface DocumentExtractor {

	/** False when no provider or key is configured. Manual workflows don't depend on this. */
	boolean isAvailable();

	String provider();

	String model();

	String promptVersion();

	/** @throws AiException when the provider fails or its output doesn't match the schema */
	Result extract(byte[] content, String contentType);

	/** {@code payload} uses the field names of the record type's own API request. */
	record Item(String type, Map<String, Object> payload) {
	}

	/** {@code readable} is false when the model couldn't reliably read a health document. */
	record Result(boolean readable, List<Item> items, Integer inputTokens, Integer outputTokens) {
	}

}
