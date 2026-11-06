package com.familyhealth.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Versioned prompts and schemas, kept as files under resources/ai (docs/06 section 8.6). */
@Component
public class PromptResources {

	private final JsonMapper json;

	public PromptResources(JsonMapper json) {
		this.json = json;
	}

	public String text(String path) {
		try (InputStream input = new ClassPathResource(path).getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Missing prompt resource " + path, ex);
		}
	}

	public JsonNode json(String path) {
		return json.readTree(text(path));
	}

}
