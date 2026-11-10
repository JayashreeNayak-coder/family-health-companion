package com.familyhealth.records;

import com.familyhealth.records.SavedRecipeDtos.Request;
import com.familyhealth.records.SavedRecipeDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/recipes")
class SavedRecipeController extends RecordController<Request, Response> {

	SavedRecipeController(SavedRecipeService service) {
		super(service, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
	}

}
