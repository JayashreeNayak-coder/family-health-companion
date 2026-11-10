package com.familyhealth.ai.gemini;

import com.familyhealth.ai.AiException;
import com.familyhealth.ai.PromptResources;
import com.familyhealth.ai.SummaryGenerator;
import com.familyhealth.ai.SummaryResponseParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sends the given records only, as JSON data in a separate part (docs/06 section 8.2). */
public class GeminiSummaryGenerator implements SummaryGenerator {

	private final GeminiClient client;

	private final JsonMapper json;

	private final String model;

	private final String fallbackModel;

	private final SummaryResponseParser parser;

	private final String prompt;

	private final JsonNode schema;

	public GeminiSummaryGenerator(GeminiClient client, JsonMapper json, String model, String fallbackModel,
			SummaryResponseParser parser, PromptResources prompts) {
		this.client = client;
		this.json = json;
		this.model = model;
		this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? null : fallbackModel;
		this.parser = parser;
		this.prompt = prompts.text("ai/summaries/" + SummaryResponseParser.VERSION + "/prompt.txt");
		this.schema = prompts.json("ai/summaries/" + SummaryResponseParser.VERSION + "/schema.json");
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
		return "summaries-" + SummaryResponseParser.VERSION;
	}

	/** Temperature 0, so restating stays literal. Tries the fallback model once on high demand. */
	@Override
	public Result generate(Request request) {
		List<GeminiClient.Part> parts = List.of(new GeminiClient.Text(json.writeValueAsString(body(request))),
				new GeminiClient.Text("Summarise the records above in the JSON structure."));
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
		Set<String> refs = request.records().stream().map(Input::ref).collect(Collectors.toSet());
		return parser.parse(output.text(), refs, output.inputTokens(), output.outputTokens());
	}

	static Map<String, Object> body(Request request) {
		Map<String, Object> body = new LinkedHashMap<>();
		Map<String, Object> period = new LinkedHashMap<>();
		period.put("from", request.from());
		period.put("to", request.to());
		body.put("period", period);
		body.put("records", request.records());
		return body;
	}

}
