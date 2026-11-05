package com.familyhealth.family;

import com.familyhealth.common.PageResponse;
import com.familyhealth.family.FamilyDtos.AcceptInvitationRequest;
import com.familyhealth.family.FamilyDtos.CreateFamilyRequest;
import com.familyhealth.family.FamilyDtos.CreateInvitationRequest;
import com.familyhealth.family.FamilyDtos.FamilyResponse;
import com.familyhealth.family.FamilyDtos.FamilySummary;
import com.familyhealth.family.FamilyDtos.InvitationCreated;
import com.familyhealth.family.FamilyDtos.InvitationPreview;
import com.familyhealth.identity.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families")
class FamilyController {

	private final FamilyService service;

	FamilyController(FamilyService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	FamilySummary create(@AuthenticationPrincipal AccountPrincipal principal,
			@Valid @RequestBody CreateFamilyRequest request) {
		return service.create(principal.accountId(), request);
	}

	@GetMapping
	PageResponse<FamilySummary> list(@AuthenticationPrincipal AccountPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(principal.accountId(), PageResponse.request(page, size, Sort.by("name", "id")));
	}

	@GetMapping("/{familyId}")
	FamilyResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId) {
		return service.get(principal.accountId(), familyId);
	}

	@PostMapping("/{familyId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	InvitationCreated invite(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId,
			@Valid @RequestBody CreateInvitationRequest request) {
		return service.invite(principal.accountId(), familyId, request);
	}

	@GetMapping("/invitations/{token}")
	InvitationPreview preview(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String token) {
		return service.preview(principal.accountId(), token);
	}

	@PostMapping("/invitations/{token}/accept")
	FamilySummary accept(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String token,
			@RequestBody(required = false) AcceptInvitationRequest request) {
		return service.accept(principal.accountId(), token, request);
	}

}
