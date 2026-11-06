package com.familyhealth.ai.gemini;

import com.familyhealth.ai.AiException;
import com.familyhealth.ai.DocumentExtractor;
import com.familyhealth.ai.ExtractionResponseParser;
import com.familyhealth.ai.PromptResources;
import java.util.Base64;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Sends the document only: no names, records, or other profile data (docs/06 section 8.2). */
public class GeminiDocumentExtractor implements DocumentExtractor {

	private final GeminiClient client;

	private final String model;

	private final String fallbackModel;

	private final ExtractionResponseParser parser;

	private final String prompt;

	private final JsonNode schema;

	public GeminiDocumentExtractor(GeminiClient client, String model, String fallbackModel,
			ExtractionResponseParser parser, PromptResources prompts) {
		this.client = client;
		this.model = model;
		this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? null : fallbackModel;
		this.parser = parser;
		this.prompt = prompts.text("ai/extraction/" + ExtractionResponseParser.VERSION + "/prompt.txt");
		this.schema = prompts.json("ai/extraction/" + ExtractionResponseParser.VERSION + "/schema.json");
	}

	@Override
	public boolean isAvailable() {
		return client.hasKey();
	}

	@Override
	public String provider() {
		return "gemini";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public String promptVersion() {
		return "extraction-" + ExtractionResponseParser.VERSION;
	}

	/** Tries the fallback model once when the main one reports high demand. */
	@Override
	public Result extract(byte[] content, String contentType) {
		List<GeminiClient.Part> parts = List.of(
				new GeminiClient.InlineData(contentType, Base64.getEncoder().encodeToString(content)),
				new GeminiClient.Text("Copy the items stated in the attached document into the JSON structure."));
		GeminiClient.Output output;
		try {
			output = client.generate(model, prompt, parts, schema);
		}
		catch (AiException ex) {
			if (ex.code() != AiException.Code.UNAVAILABLE || fallbackModel == null) {
				throw ex;
			}
			output = client.generate(fallbackModel, prompt, parts, schema);
		}
		return parser.parse(output.text(), output.inputTokens(), output.outputTokens());
	}

}
