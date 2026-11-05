package com.familyhealth.records;

import com.familyhealth.records.ConditionDtos.Request;
import com.familyhealth.records.ConditionDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/conditions")
class ConditionController extends RecordController<Request, Response> {

	ConditionController(ConditionService service) {
		super(service, Sort.by("name", "id"));
	}

}
