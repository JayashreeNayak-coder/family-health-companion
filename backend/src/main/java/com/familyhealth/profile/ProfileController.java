package com.familyhealth.profile;

import com.familyhealth.common.PageResponse;
import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.photo.PhotoStore;
import com.familyhealth.profile.ProfileDtos.CreateProfileRequest;
import com.familyhealth.profile.ProfileDtos.GrantRequest;
import com.familyhealth.profile.ProfileDtos.GrantResponse;
import com.familyhealth.profile.ProfileDtos.ProfileResponse;
import com.familyhealth.profile.ProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1")
class ProfileController {

	private final ProfileService service;

	ProfileController(ProfileService service) {
		this.service = service;
	}

	@PostMapping("/families/{familyId}/profiles")
	@ResponseStatus(HttpStatus.CREATED)
	ProfileResponse create(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID familyId,
			@Valid @RequestBody CreateProfileRequest request) {
		return service.create(principal.accountId(), familyId, request);
	}

	@GetMapping("/profiles")
	PageResponse<ProfileResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(principal.accountId(), PageResponse.request(page, size, Sort.unsorted()));
	}

	@GetMapping("/profiles/{profileId}")
	ProfileResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.get(principal.accountId(), profileId);
	}

	@PutMapping("/profiles/{profileId}")
	ProfileResponse update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return service.update(principal.accountId(), profileId, request);
	}

	@PutMapping(path = "/profiles/{profileId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ProfileResponse setPhoto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam(name = "file", required = false) MultipartFile file) {
		return service.setPhoto(principal.accountId(), profileId, file);
	}

	@DeleteMapping("/profiles/{profileId}/photo")
	ProfileResponse deletePhoto(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.deletePhoto(principal.accountId(), profileId);
	}

	@GetMapping("/profiles/{profileId}/photo")
	ResponseEntity<byte[]> photo(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return PhotoStore.response(service.photo(principal.accountId(), profileId));
	}

	@DeleteMapping("/profiles/{profileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		service.delete(principal.accountId(), profileId);
	}

	@GetMapping("/profiles/{profileId}/grants")
	List<GrantResponse> listGrants(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.listGrants(principal.accountId(), profileId);
	}

	@PutMapping("/profiles/{profileId}/grants/{accountId}")
	GrantResponse putGrant(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID accountId, @Valid @RequestBody GrantRequest request) {
		return service.putGrant(principal.accountId(), profileId, accountId, request.level());
	}

	@DeleteMapping("/profiles/{profileId}/grants/{accountId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteGrant(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID accountId) {
		service.deleteGrant(principal.accountId(), profileId, accountId);
	}

}
