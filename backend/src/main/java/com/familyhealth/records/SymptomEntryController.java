package com.familyhealth.records;

import com.familyhealth.records.SymptomEntryDtos.Request;
import com.familyhealth.records.SymptomEntryDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/symptoms")
class SymptomEntryController extends RecordController<Request, Response> {

	SymptomEntryController(SymptomEntryService service) {
		super(service, Sort.by(Sort.Order.desc("occurredOn").nullsLast(), Sort.Order.desc("occurredTime").nullsLast(),
				Sort.Order.asc("id")));
	}

}
