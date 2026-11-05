package com.familyhealth.records;

import com.familyhealth.records.DietaryRestrictionDtos.Request;
import com.familyhealth.records.DietaryRestrictionDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/dietary-restrictions")
class DietaryRestrictionController extends RecordController<Request, Response> {

	DietaryRestrictionController(DietaryRestrictionService service) {
		super(service, Sort.by("kind", "description", "id"));
	}

}
