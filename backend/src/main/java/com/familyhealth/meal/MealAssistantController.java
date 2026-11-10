package com.familyhealth.meal;

import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.meal.MealAssistantDtos.GeneralRequest;
import com.familyhealth.meal.MealAssistantDtos.HealthAwareRequest;
import com.familyhealth.meal.MealAssistantDtos.Response;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Recipes are returned, not stored. Saving one is a separate call (docs/05 section 7). */
@RestController
class MealAssistantController {

	private final MealAssistantService service;

	MealAssistantController(MealAssistantService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/meal-assistant/general")
	Response general(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody GeneralRequest request) {
		return service.general(principal.accountId(), request);
	}

	/** Needs VIEW on the profile (docs/05 section 7). */
	@PostMapping("/api/v1/profiles/{profileId}/meal-assistant/health-aware")
	Response healthAware(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody HealthAwareRequest request) {
		return service.healthAware(principal.accountId(), profileId, request);
	}

}
