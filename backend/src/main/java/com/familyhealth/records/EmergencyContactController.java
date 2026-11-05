package com.familyhealth.records;

import com.familyhealth.records.EmergencyContactDtos.Request;
import com.familyhealth.records.EmergencyContactDtos.Response;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/emergency-contacts")
class EmergencyContactController extends RecordController<Request, Response> {

	EmergencyContactController(EmergencyContactService service) {
		super(service, Sort.by("name", "id"));
	}

}
