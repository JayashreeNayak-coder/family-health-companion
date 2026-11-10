package com.familyhealth.records;

import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.records.FoodLogEntryDtos.FromRecipeRequest;
import com.familyhealth.records.FoodLogEntryDtos.Request;
import com.familyhealth.records.FoodLogEntryDtos.Response;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/food-log")
class FoodLogEntryController extends RecordController<Request, Response> {

	private final FoodLogEntryService service;

	FoodLogEntryController(FoodLogEntryService service) {
		super(service, Sort.by(Sort.Order.desc("occurredOn").nullsLast(), Sort.Order.desc("occurredTime").nullsLast(),
				Sort.Order.asc("id")));
		this.service = service;
	}

	@PostMapping("/from-recipe/{recipeId}")
	@ResponseStatus(HttpStatus.CREATED)
	public Response fromRecipe(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID recipeId, @Valid @RequestBody FromRecipeRequest request) {
		return service.createFromRecipe(principal.accountId(), profileId, recipeId, request);
	}

}
