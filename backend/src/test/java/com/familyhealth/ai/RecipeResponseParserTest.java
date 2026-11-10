package com.familyhealth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familyhealth.ai.AiException.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class RecipeResponseParserTest {

	private final RecipeResponseParser parser = new RecipeResponseParser(JsonMapper.builder().build());

	private static final String RECIPE = """
			{"title": " Synthetic dal ", "minutes": 30, "servings": null,
			 "ingredients": ["1 cup lentils", "salt"], "steps": ["Rinse the lentils.", "Cook them."]}""";

	@Test
	void readsValidRecipesAndTrimsText() {
		RecipeGenerator.Result result = parser.parse("{\"recipes\": [" + RECIPE + "]}", 50, 80);

		assertThat(result.recipes()).hasSize(1);
		RecipeGenerator.Recipe recipe = result.recipes().get(0);
		assertThat(recipe.title()).isEqualTo("Synthetic dal");
		assertThat(recipe.minutes()).isEqualTo(30);
		assertThat(recipe.servings()).isNull();
		assertThat(recipe.ingredients()).containsExactly("1 cup lentils", "salt");
		assertThat(result.inputTokens()).isEqualTo(50);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not json",
			"[]",
			"{\"recipes\": []}",
			"{\"recipes\": [RECIPE, RECIPE, RECIPE, RECIPE]}",
			"{\"recipes\": [RECIPE], \"advice\": \"eat more\"}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": 5, \"servings\": 1, \"ingredients\": [\"a\"], \"steps\": [\"b\"], \"healthy\": true}]}",
			"{\"recipes\": [{\"title\": \"\", \"minutes\": 5, \"servings\": 1, \"ingredients\": [\"a\"], \"steps\": [\"b\"]}]}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": -1, \"servings\": 1, \"ingredients\": [\"a\"], \"steps\": [\"b\"]}]}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": 2.5, \"servings\": 1, \"ingredients\": [\"a\"], \"steps\": [\"b\"]}]}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": 5, \"servings\": 1, \"ingredients\": [], \"steps\": [\"b\"]}]}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": 5, \"servings\": 1, \"ingredients\": [1], \"steps\": [\"b\"]}]}",
			"{\"recipes\": [{\"title\": \"x\", \"minutes\": 5, \"servings\": 1, \"ingredients\": [\"a\"]}]}" })
	void rejectsAnythingOutsideTheSchema(String output) {
		assertThatThrownBy(() -> parser.parse(output.replace("RECIPE", RECIPE), null, null))
			.isInstanceOf(AiException.class)
			.extracting(ex -> ((AiException) ex).code())
			.isEqualTo(Code.MALFORMED_RESPONSE);
	}

}
