package com.familyhealth.records;

import com.familyhealth.records.AllergyDtos.Request;
import com.familyhealth.records.AllergyDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/allergies")
class AllergyController extends RecordController<Request, Response> {

	AllergyController(AllergyService service) {
		super(service, Sort.by("allergen", "id"));
	}

}
