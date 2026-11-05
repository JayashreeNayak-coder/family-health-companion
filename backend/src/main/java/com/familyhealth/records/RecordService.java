package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * The create, read, update, and delete pattern shared by profile-owned records (docs/05 section
 * 5). Access is checked here, so no record type can forget it.
 *
 * @param <E> the entity
 * @param <Q> the request body
 * @param <S> the response body
 */
public abstract class RecordService<E extends ProfileRecord, Q extends VersionedRequest, S> {

	private final ProfileRecordRepository<E> repository;

	private final ProfileAccess access;

	protected RecordService(ProfileRecordRepository<E> repository, ProfileAccess access) {
		this.repository = repository;
		this.access = access;
	}

	protected abstract E newRecord(UUID profileId, UUID accountId, Q request);

	protected abstract void apply(E record, Q request);

	protected abstract S toResponse(E record);

	@Transactional(readOnly = true)
	public PageResponse<S> list(UUID accountId, UUID profileId, Pageable pageable) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return PageResponse.from(repository.findByProfileId(profileId, pageable), this::toResponse);
	}

	@Transactional(readOnly = true)
	public S get(UUID accountId, UUID profileId, UUID id) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return toResponse(load(profileId, id));
	}

	@Transactional
	public S create(UUID accountId, UUID profileId, Q request) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		return toResponse(repository.saveAndFlush(newRecord(profileId, accountId, request)));
	}

	@Transactional
	public S update(UUID accountId, UUID profileId, UUID id, Q request) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		E record = load(profileId, id);
		record.requireVersion(request.version());
		apply(record, request);
		return toResponse(repository.saveAndFlush(record));
	}

	@Transactional
	public void delete(UUID accountId, UUID profileId, UUID id) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		repository.delete(load(profileId, id));
	}

	protected E load(UUID profileId, UUID id) {
		return repository.findByIdAndProfileId(id, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
	}

}
