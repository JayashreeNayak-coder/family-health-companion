package com.familyhealth.records;

import com.familyhealth.identity.AccountPrincipal;
import com.familyhealth.records.AttachmentService.Attachment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/attachments")
class AttachmentController {

	record AttachRequest(@NotNull RecordType recordType, @NotNull UUID recordId, @NotNull UUID documentId) {
	}

	private final AttachmentService service;

	AttachmentController(AttachmentService service) {
		this.service = service;
	}

	@GetMapping
	public List<Attachment> list(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam RecordType recordType, @RequestParam(required = false) UUID recordId) {
		return service.list(principal.accountId(), profileId, recordType, recordId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Attachment attach(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody AttachRequest request) {
		return service.attach(principal.accountId(), profileId, request.recordType(), request.recordId(),
				request.documentId());
	}

	@DeleteMapping("/{attachmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void detach(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID attachmentId) {
		service.detach(principal.accountId(), profileId, attachmentId);
	}

}
