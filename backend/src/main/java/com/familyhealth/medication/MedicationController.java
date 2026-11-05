package com.familyhealth.medication;

import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.medication.MedicationDtos.AdherenceRequest;
import com.familyhealth.medication.MedicationDtos.AdherenceResponse;
import com.familyhealth.medication.MedicationDtos.DueDose;
import com.familyhealth.medication.MedicationDtos.Request;
import com.familyhealth.medication.MedicationDtos.Response;
import com.familyhealth.medication.MedicationDtos.ScheduleResponse;
import com.familyhealth.medication.MedicationDtos.SchedulesRequest;
import com.familyhealth.records.RecordController;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/medications")
class MedicationController extends RecordController<Request, Response> {

	private final MedicationService service;

	MedicationController(MedicationService service) {
		super(service, Sort.by("name", "id"));
		this.service = service;
	}

	@GetMapping("/due")
	List<DueDose> due(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam LocalDate date) {
		return service.due(principal.accountId(), profileId, date);
	}

	@GetMapping("/{medicationId}/schedules")
	List<ScheduleResponse> listSchedules(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @PathVariable UUID medicationId) {
		return service.listSchedules(principal.accountId(), profileId, medicationId);
	}

	@PutMapping("/{medicationId}/schedules")
	List<ScheduleResponse> replaceSchedules(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @PathVariable UUID medicationId,
			@Valid @RequestBody SchedulesRequest request) {
		return service.replaceSchedules(principal.accountId(), profileId, medicationId, request.schedules());
	}

	@PostMapping("/{medicationId}/adherence")
	@ResponseStatus(HttpStatus.CREATED)
	AdherenceResponse recordAdherence(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @PathVariable UUID medicationId,
			@Valid @RequestBody AdherenceRequest request) {
		return service.recordAdherence(principal.accountId(), profileId, medicationId, request);
	}

}
