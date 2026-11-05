package com.familyhealth.records;

import com.familyhealth.common.PageResponse;
import com.familyhealth.identity.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The endpoints from docs/05 section 5. A subclass only names its path and sort order. */
public abstract class RecordController<Q extends VersionedRequest, S> {

	private final RecordService<?, Q, S> service;

	private final Sort sort;

	protected RecordController(RecordService<?, Q, S> service, Sort sort) {
		this.service = service;
		this.sort = sort;
	}

	@GetMapping
	public PageResponse<S> list(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(principal.accountId(), profileId, PageResponse.request(page, size, sort));
	}

	@GetMapping("/{id}")
	public S get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id) {
		return service.get(principal.accountId(), profileId, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public S create(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody Q request) {
		return service.create(principal.accountId(), profileId, request);
	}

	@PutMapping("/{id}")
	public S update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id, @Valid @RequestBody Q request) {
		return service.update(principal.accountId(), profileId, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@PathVariable UUID id) {
		service.delete(principal.accountId(), profileId, id);
	}

}
