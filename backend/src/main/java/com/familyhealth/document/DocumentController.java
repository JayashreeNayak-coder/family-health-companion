package com.familyhealth.document;

import com.familyhealth.common.PageResponse;
import com.familyhealth.identity.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/documents")
class DocumentController {

	/** The multipart form. The file's type comes from its content, never from these fields. */
	record UploadForm(MultipartFile file, DocumentCategory category,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentOn,
			@Size(max = 200) String description) {
	}

	private final DocumentService service;

	DocumentController(DocumentService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentSummary upload(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @ModelAttribute UploadForm form) {
		return service.upload(principal.accountId(), profileId, form.file(), form.category(), form.documentOn(),
				form.description());
	}

	@GetMapping
	public PageResponse<DocumentSummary> list(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(principal.accountId(), profileId,
				PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
	}

	@GetMapping("/{documentId}")
	public DocumentSummary get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId) {
		return service.get(principal.accountId(), profileId, documentId);
	}

	/**
	 * Always an attachment, never rendered inline from this origin. Spring Security adds
	 * {@code nosniff}; caching is turned off here explicitly (docs/05 section 6).
	 */
	@GetMapping("/{documentId}/content")
	public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable UUID profileId, @PathVariable UUID documentId) {
		DocumentService.Content content = service.open(principal.accountId(), profileId, documentId);
		DocumentSummary summary = content.summary();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(summary.contentType()))
			.contentLength(summary.sizeBytes())
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(summary.originalFilename(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(new InputStreamResource(content.stream()));
	}

	@DeleteMapping("/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID documentId) {
		service.delete(principal.accountId(), profileId, documentId);
	}

}
