package com.familyhealth.meal;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.FakeRecipeGenerator;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.familyhealth.ai.AiException;
import com.familyhealth.ai.RecipeGenerator;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class MealAssistantIntegrationTest extends IntegrationTestBase {

	private static final String GENERAL = "/api/v1/meal-assistant/general";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeRecipeGenerator fake;

	private ProfileFixture f;

	@BeforeEach
	void setUp() throws Exception {
		fake.reset();
		f = new ProfileFixture(mockMvc);
	}

	private static RecipeGenerator.Result recipes(RecipeGenerator.Recipe... recipes) {
		return new RecipeGenerator.Result(List.of(recipes), 40, 90);
	}

	private static RecipeGenerator.Recipe recipe(String title, String... ingredients) {
		return new RecipeGenerator.Recipe(title, 25, 2, List.of(ingredients), List.of("Cook everything together."));
	}

	private long usage(String accountId, String outcome) {
		return jdbc.queryForObject("select count(*) from ai_usage_event where user_account_id = ?::uuid and "
				+ "feature = 'MEAL_GENERAL' and outcome = ?", Long.class, accountId, outcome);
	}

	@Test
	void generalModeSendsOnlyWhatWasTypedAndLabelsTheAnswer() throws Exception {
		// Records exist for a profile the caller can see, and none of them may reach the provider.
		f.editor.post("/api/v1/profiles/" + f.profileId + "/allergies", Map.of("allergen", "Synthetic sesame"));
		fake.willAnswer(recipes(recipe("Synthetic dal", "1 cup lentils", "salt")));

		MockHttpServletResponse response = f.viewer.post(GENERAL, Map.of("ingredients", List.of(" lentils ", "rice"),
				"maxMinutes", 30, "preferences", "Indian", "avoid", List.of("peanuts")));

		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat(JsonPath.<String>read(body, "$.contentType")).isEqualTo("AI_GENERATED");
		assertThat(JsonPath.<String>read(body, "$.mode")).isEqualTo("GENERAL");
		assertThat(JsonPath.<String>read(body, "$.promptVersion")).isEqualTo("recipes-v1");
		assertThat(JsonPath.<String>read(body, "$.disclaimer")).contains("not medical or nutrition advice");
		assertThat(JsonPath.<List<String>>read(body, "$.limitations")).anyMatch(l -> l.contains("doesn't use"))
			.anyMatch(l -> l.contains("Check every ingredient yourself"));
		assertThat(JsonPath.<String>read(body, "$.recipes[0].title")).isEqualTo("Synthetic dal");
		assertThat(JsonPath.<List<String>>read(body, "$.recipes[0].avoidMatches")).isEmpty();

		assertThat(fake.requests()).containsExactly(
				new RecipeGenerator.Request(List.of("lentils", "rice"), 30, "Indian", List.of("peanuts")));
		assertThat(fake.requests().get(0).toString()).doesNotContain("sesame").doesNotContain("Synthetic");
		assertThat(usage(f.viewerId, "REQUESTED")).isEqualTo(1);
		assertThat(usage(f.viewerId, "SUCCEEDED")).isEqualTo(1);
	}

	@Test
	void aRecipeMentioningAnIngredientToAvoidIsFlaggedNotHidden() throws Exception {
		fake.willAnswer(recipes(recipe("Satay noodles", "2 tbsp peanut butter"), recipe("Plain rice", "rice")));

		String body = f.viewer.post(GENERAL, Map.of("ingredients", List.of("noodles"), "avoid", List.of("Peanuts")))
			.getContentAsString();

		assertThat(JsonPath.<List<String>>read(body, "$.recipes[0].avoidMatches")).containsExactly("Peanuts");
		assertThat(JsonPath.<List<String>>read(body, "$.recipes[1].avoidMatches")).isEmpty();
	}

	@Test
	void withNothingToAvoidItSaysNothingWasChecked() throws Exception {
		fake.willAnswer(recipes(recipe("Synthetic dal", "lentils")));

		String body = f.viewer.post(GENERAL, Map.of("ingredients", List.of("lentils"))).getContentAsString();

		assertThat(JsonPath.<List<String>>read(body, "$.limitations"))
			.anyMatch(l -> l.contains("nothing was checked for allergies"));
	}

	@Test
	void inputIsValidatedAndSignInIsRequired() throws Exception {
		assertThat(f.signedOut.post(GENERAL, Map.of("ingredients", List.of("rice"))).getStatus()).isEqualTo(401);
		MockHttpServletResponse empty = f.viewer.post(GENERAL, Map.of("ingredients", List.of()));
		assertThat(empty.getStatus()).isEqualTo(400);
		assertThat(f.viewer.post(GENERAL, Map.of("ingredients", List.of(" "))).getStatus()).isEqualTo(400);
		assertThat(f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice"), "maxMinutes", 1)).getStatus())
			.isEqualTo(400);
		assertThat(f.viewer.post(GENERAL, Map.of("ingredients", List.of("x".repeat(81)))).getStatus()).isEqualTo(400);
		assertThat(fake.requests()).isEmpty();
	}

	@Test
	void providerFailuresAreReportedWithoutBreakingAnythingElse() throws Exception {
		fake.setAvailable(false);
		MockHttpServletResponse off = f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice")));
		assertThat(off.getStatus()).isEqualTo(503);
		assertThat(read(off, "$.code")).isEqualTo("AI_UNAVAILABLE");
		assertThat(usage(f.viewerId, "REQUESTED")).isZero();
		fake.setAvailable(true);

		fake.willAnswer(new AiException(AiException.Code.TIMEOUT, "slow"),
				new AiException(AiException.Code.MALFORMED_RESPONSE, "bad"),
				new AiException(AiException.Code.BLOCKED, "no"));
		MockHttpServletResponse timeout = f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice")));
		assertThat(timeout.getStatus()).isEqualTo(503);
		assertThat(read(timeout, "$.code")).isEqualTo("AI_UNAVAILABLE");
		MockHttpServletResponse malformed = f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice")));
		assertThat(malformed.getStatus()).isEqualTo(502);
		assertThat(read(malformed, "$.code")).isEqualTo("AI_BAD_RESPONSE");
		assertThat(f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice"))).getStatus()).isEqualTo(422);
		assertThat(usage(f.viewerId, "TIMEOUT")).isEqualTo(1);
		assertThat(usage(f.viewerId, "REQUESTED")).isEqualTo(3);
	}

	@Test
	void theDailyLimitApplies() throws Exception {
		jdbc.update("""
				insert into ai_usage_event (id, user_account_id, feature, occurred_at, outcome)
				select gen_random_uuid(), ?::uuid, 'MEAL_GENERAL', now(), 'REQUESTED' from generate_series(1, 25)
				""", f.viewerId);

		MockHttpServletResponse limited = f.viewer.post(GENERAL, Map.of("ingredients", List.of("rice")));

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(read(limited, "$.code")).isEqualTo("AI_LIMIT_REACHED");
		assertThat(fake.requests()).isEmpty();
	}

	@Test
	void aSavedRecipeCanBeAddedToTheFoodLog() throws Exception {
		String base = "/api/v1/profiles/" + f.profileId;
		Map<String, Object> recipe = Map.of("title", "Synthetic dal", "ingredients", List.of("1 cup lentils"), "steps",
				List.of("Cook the lentils."), "mode", "GENERAL", "promptVersion", "recipes-v1");
		assertThat(f.viewer.post(base + "/recipes", recipe).getStatus()).isEqualTo(403);
		MockHttpServletResponse saved = created(f.editor.post(base + "/recipes", recipe));
		String recipeId = read(saved, "$.id");
		assertThat(read(saved, "$.contentType")).isEqualTo("AI_GENERATED");
		assertThat(JsonPath.<List<String>>read(saved.getContentAsString(), "$.ingredients"))
			.containsExactly("1 cup lentils");

		String fromRecipe = base + "/food-log/from-recipe/" + recipeId;
		Map<String, Object> when = Map.of("mealType", "DINNER", "occurredOn", "2026-03-01", "occurredTime", "19:30");
		assertThat(f.viewer.post(fromRecipe, when).getStatus()).isEqualTo(403);
		assertThat(f.editor.post(fromRecipe, Map.of("occurredTime", "19:30")).getStatus()).isEqualTo(400);
		MockHttpServletResponse entry = created(f.editor.post(fromRecipe, when));
		assertThat(read(entry, "$.description")).isEqualTo("Synthetic dal");
		assertThat(read(entry, "$.mealType")).isEqualTo("DINNER");
		assertThat(read(entry, "$.savedRecipeId")).isEqualTo(recipeId);

		// A recipe ID from another profile is not found, even with access to both profiles.
		String other = "/api/v1/profiles/" + f.otherProfileId + "/food-log/from-recipe/" + recipeId;
		assertThat(f.manager.post(other, Map.of()).getStatus()).isEqualTo(404);

		// Deleting the recipe keeps the food log entry.
		String entryId = read(entry, "$.id");
		assertThat(f.editor.delete(base + "/recipes/" + recipeId).getStatus()).isEqualTo(204);
		MockHttpServletResponse kept = f.viewer.get(base + "/food-log/" + entryId);
		assertThat(kept.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<Object>read(kept.getContentAsString(), "$.savedRecipeId")).isNull();
	}


	private String healthAware(String profileId) {
		return "/api/v1/profiles/" + profileId + "/meal-assistant/health-aware";
	}

	@Test
	void healthAwareModeSendsOnlyAllowedFieldsAndTheChosenConditions() throws Exception {
		String base = "/api/v1/profiles/" + f.profileId;
		f.editor.post(base + "/allergies",
				Map.of("allergen", "Sesame", "reactionAsRecorded", "Hives recorded", "notes", "Private allergy note"));
		f.editor.post(base + "/dietary-restrictions", Map.of("kind", "RESTRICTION", "description", "Vegetarian"));
		f.editor.post(base + "/dietary-restrictions", Map.of("kind", "PREFERENCE", "description", "Likes spicy food"));
		String chosen = read(created(f.editor.post(base + "/conditions",
				Map.of("name", "Synthetic chosen condition", "status", "ACTIVE", "notes", "Private condition note"))), "$.id");
		f.editor.post(base + "/conditions", Map.of("name", "Synthetic unchosen condition", "status", "ACTIVE"));
		fake.willAnswer(recipes(recipe("Sesame noodles", "1 tbsp sesame oil"), recipe("Vegetable rice", "rice")));

		MockHttpServletResponse response = f.viewer.post(healthAware(f.profileId),
				Map.of("ingredients", List.of("noodles", "rice"), "avoid", List.of("mushrooms"), "conditionIds",
						List.of(chosen)));

		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		RecipeGenerator.Request sent = fake.requests().get(0);
		assertThat(sent.profile()).isEqualTo(new RecipeGenerator.ProfileContext(List.of("Sesame"), List.of("Vegetarian"),
				List.of("Likes spicy food"), List.of("Synthetic chosen condition")));
		assertThat(sent.avoid()).containsExactly("mushrooms");
		assertThat(sent.toString()).doesNotContain("Hives").doesNotContain("Private").doesNotContain("unchosen")
			.doesNotContain("Child Synthetic").doesNotContain(f.profileId);

		String body = response.getContentAsString();
		assertThat(JsonPath.<String>read(body, "$.mode")).isEqualTo("HEALTH_AWARE");
		assertThat(JsonPath.<String>read(body, "$.disclaimer")).contains("not confirmed safe");
		assertThat(JsonPath.<List<String>>read(body, "$.considered.allergies")).containsExactly("Sesame");
		assertThat(JsonPath.<List<String>>read(body, "$.considered.conditions"))
			.containsExactly("Synthetic chosen condition");
		assertThat(JsonPath.<List<String>>read(body, "$.recipes[0].avoidMatches")).containsExactly("Sesame");
		assertThat(JsonPath.<List<String>>read(body, "$.recipes[1].avoidMatches")).isEmpty();
		assertThat(JsonPath.<List<String>>read(body, "$.limitations"))
			.anyMatch(l -> l.contains("not a diet for any condition"))
			.noneMatch(l -> l.contains("No allergies"));
	}

	@Test
	void healthAwareModeDisclosesWhatIsMissing() throws Exception {
		fake.willAnswer(recipes(recipe("Plain rice", "rice")));

		String body = f.viewer.post(healthAware(f.profileId), Map.of("ingredients", List.of("rice")))
			.getContentAsString();

		assertThat(JsonPath.<List<String>>read(body, "$.limitations"))
			.anyMatch(l -> l.contains("No allergies are recorded"))
			.anyMatch(l -> l.contains("No dietary restrictions or preferences"))
			.anyMatch(l -> l.contains("No health conditions were included"));
		assertThat(fake.requests().get(0).profile())
			.isEqualTo(new RecipeGenerator.ProfileContext(List.of(), List.of(), List.of(), List.of()));
	}

	@Test
	void healthAwareModeFollowsProfileAccess() throws Exception {
		Map<String, Object> request = Map.of("ingredients", List.of("rice"));
		assertThat(f.signedOut.post(healthAware(f.profileId), request).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(healthAware(f.profileId), request).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.post(healthAware(f.profileId), request).getStatus()).isEqualTo(404);

		// A condition from another profile can't be pulled in by ID.
		String other = read(created(f.manager.post("/api/v1/profiles/" + f.otherProfileId + "/conditions",
				Map.of("name", "Other profile condition", "status", "ACTIVE"))), "$.id");
		MockHttpServletResponse foreign = f.manager.post(healthAware(f.profileId),
				Map.of("ingredients", List.of("rice"), "conditionIds", List.of(other)));
		assertThat(foreign.getStatus()).isEqualTo(400);
		assertThat(fake.requests()).isEmpty();

		// Access is checked before availability, so AI being off reveals nothing.
		fake.setAvailable(false);
		assertThat(f.outsider.post(healthAware(f.profileId), request).getStatus()).isEqualTo(404);
		assertThat(f.viewer.post(healthAware(f.profileId), request).getStatus()).isEqualTo(503);
	}

}
