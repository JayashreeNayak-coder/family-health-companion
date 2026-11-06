package com.familyhealth.records;

import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.records.LabResultDtos.Request;
import com.familyhealth.records.LabResultDtos.Response;
import com.familyhealth.records.LabResultDtos.Trend;
import com.familyhealth.records.LabResultDtos.TrendSeries;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/lab-results")
class LabResultController extends RecordController<Request, Response> {

	private final LabResultService service;

	LabResultController(LabResultService service) {
		super(service, Sort.by(Sort.Order.desc("resultOn").nullsLast(), Sort.Order.asc("testKey"),
				Sort.Order.asc("id")));
		this.service = service;
	}

	@GetMapping("/trend-series")
	public List<TrendSeries> series(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId) {
		return service.series(principal.accountId(), profileId);
	}

	@GetMapping("/trends")
	public Trend trend(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam String testKey, @RequestParam(required = false) String unit) {
		return service.trend(principal.accountId(), profileId, testKey, unit);
	}

}
