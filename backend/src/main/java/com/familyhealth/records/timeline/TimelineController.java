package com.familyhealth.records.timeline;

import com.familyhealth.identity.AccountPrincipal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TimelineController {

	private final TimelineService service;

	TimelineController(TimelineService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/profiles/{profileId}/timeline")
	TimelineService.Timeline get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Set<TimelineType> types) {
		return service.get(principal.accountId(), profileId, from, to, types);
	}

}
