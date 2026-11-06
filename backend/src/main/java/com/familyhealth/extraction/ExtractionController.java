package com.familyhealth.extraction;

import com.familyhealth.extraction.ExtractionDtos.ApproveRequest;
import com.familyhealth.extraction.ExtractionDtos.CorrectRequest;
import com.familyhealth.extraction.ExtractionDtos.DraftResponse;
import com.familyhealth.extraction.ExtractionDtos.ItemResponse;
import com.familyhealth.extraction.ExtractionDtos.JobResponse;
import com.familyhealth.extraction.ExtractionDtos.RejectRequest;
import com.familyhealth.identity.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/documents/{documentId}")
class ExtractionController {

	private final ExtractionService service;

	ExtractionController(ExtractionService service) {
		this.service = service;
	}

	@PostMapping("/extraction")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JobResponse request(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId) {
		return service.request(principal.accountId(), profileId, documentId);
	}

	@GetMapping("/extraction")
	public JobResponse job(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId) {
		return service.job(principal.accountId(), profileId, documentId);
	}

	@GetMapping("/draft")
	public DraftResponse draft(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId) {
		return service.draft(principal.accountId(), profileId, documentId);
	}

	@PostMapping("/draft/reject")
	public DraftResponse rejectRemaining(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @PathVariable UUID documentId) {
		return service.rejectRemaining(principal.accountId(), profileId, documentId);
	}

	@PatchMapping("/draft/items/{itemId}")
	public ItemResponse correct(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId, @PathVariable UUID itemId, @Valid @RequestBody CorrectRequest request) {
		return service.correct(principal.accountId(), profileId, documentId, itemId, request.payload(),
				request.version());
	}

	@PostMapping("/draft/items/{itemId}/approve")
	public ItemResponse approve(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId, @PathVariable UUID itemId, @Valid @RequestBody ApproveRequest request) {
		return service.approve(principal.accountId(), profileId, documentId, itemId, request.payload(),
				request.version());
	}

	@PostMapping("/draft/items/{itemId}/reject")
	public ItemResponse reject(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId, @PathVariable UUID itemId, @Valid @RequestBody RejectRequest request) {
		return service.reject(principal.accountId(), profileId, documentId, itemId, request.version());
	}

}
