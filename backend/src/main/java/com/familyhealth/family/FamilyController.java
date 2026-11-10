package com.familyhealth.family;

import com.familyhealth.common.PageResponse;
import com.familyhealth.family.FamilyDtos.AcceptInvitationRequest;
import com.familyhealth.family.FamilyDtos.CreateFamilyRequest;
import com.familyhealth.family.FamilyDtos.CreateInvitationRequest;
import com.familyhealth.family.FamilyDtos.FamilyResponse;
import com.familyhealth.family.FamilyDtos.FamilySummary;
import com.familyhealth.family.FamilyDtos.InvitationCreated;
import com.familyhealth.family.FamilyDtos.InvitationPreview;
import com.familyhealth.family.FamilyDtos.RenameFamilyRequest;
import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.photo.PhotoStore;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

	@PutMapping("/{familyId}")
	FamilySummary rename(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId,
			@Valid @RequestBody RenameFamilyRequest request) {
		return service.rename(principal.accountId(), familyId, request);
	}

	@DeleteMapping("/{familyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId) {
		service.delete(principal.accountId(), familyId);
	}

	@PutMapping(path = "/{familyId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	FamilySummary setPhoto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId,
			@RequestParam(name = "file", required = false) MultipartFile file) {
		return service.setPhoto(principal.accountId(), familyId, file);
	}

	@DeleteMapping("/{familyId}/photo")
	FamilySummary deletePhoto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId) {
		return service.deletePhoto(principal.accountId(), familyId);
	}

	@GetMapping("/{familyId}/photo")
	ResponseEntity<byte[]> photo(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId) {
		return PhotoStore.response(service.photo(principal.accountId(), familyId));
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
