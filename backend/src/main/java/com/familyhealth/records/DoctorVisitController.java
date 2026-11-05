package com.familyhealth.records;

import com.familyhealth.records.DoctorVisitDtos.Request;
import com.familyhealth.records.DoctorVisitDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/visits")
class DoctorVisitController extends RecordController<Request, Response> {

	DoctorVisitController(DoctorVisitService service) {
		super(service, Sort.by(Sort.Order.desc("occurredOn").nullsLast(), Sort.Order.desc("occurredTime").nullsLast(),
				Sort.Order.asc("id")));
	}

}
