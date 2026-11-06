package com.familyhealth.records;

import com.familyhealth.records.FoodLogEntryDtos.Request;
import com.familyhealth.records.FoodLogEntryDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/food-log")
class FoodLogEntryController extends RecordController<Request, Response> {

	FoodLogEntryController(FoodLogEntryService service) {
		super(service, Sort.by(Sort.Order.desc("occurredOn").nullsLast(), Sort.Order.desc("occurredTime").nullsLast(),
				Sort.Order.asc("id")));
	}

}
