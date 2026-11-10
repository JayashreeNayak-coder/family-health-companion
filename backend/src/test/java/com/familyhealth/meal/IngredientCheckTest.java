package com.familyhealth.meal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientCheckTest {

	@Test
	void flagsTermsInAnyPartOfTheRecipeIgnoringCaseAndPlurals() {
		List<String> found = IngredientCheck.matches(List.of("Peanuts", "tomatoes", "shellfish"), "Satay noodles",
				List.of("2 tbsp PEANUT butter", "1 tomato"), List.of("Stir in the sauce."));

		assertThat(found).containsExactly("Peanuts", "tomatoes");
	}

	@Test
	void matchingIsLooseSoItErrsTowardsAFlag() {
		assertThat(IngredientCheck.matches(List.of("nut"), "Coconut rice", List.of("rice"), List.of("Cook.")))
			.containsExactly("nut");
	}

	@Test
	void noTermsMeansNoFlags() {
		assertThat(IngredientCheck.matches(List.of(), "Dal", List.of("lentils"), List.of("Cook."))).isEmpty();
		assertThat(IngredientCheck.stems("  ")).isEmpty();
		assertThat(IngredientCheck.stems("gas")).containsExactly("gas");
	}

}
