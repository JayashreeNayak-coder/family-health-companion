package com.familyhealth.summary;

import com.familyhealth.common.PageResponse;
import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.summary.SummaryDtos.Request;
import com.familyhealth.summary.SummaryDtos.Response;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Generating needs VIEW, like reading (docs/05 section 7). Deleting needs EDIT. */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/summaries")
class SummaryController {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

	private final SummaryService service;

	SummaryController(SummaryService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Response generate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody Request request) {
		return service.generate(principal.accountId(), profileId, request);
	}

	@GetMapping
	PageResponse<Response> list(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(principal.accountId(), profileId, PageResponse.request(page, size, NEWEST_FIRST));
	}

	@GetMapping("/{id}")
	Response get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id) {
		return service.get(principal.accountId(), profileId, id);
	}

	@GetMapping("/{id}/pdf")
	ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id) {
		byte[] pdf = service.exportPdf(principal.accountId(), profileId, id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("health-record-summary.pdf").build().toString())
			.body(pdf);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id) {
		service.delete(principal.accountId(), profileId, id);
	}

}
