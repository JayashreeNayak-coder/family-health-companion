package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.GenericTypeResolver;
import org.springframework.beans.factory.annotation.Autowired;
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

	private RecordSupport support;

	protected RecordService(ProfileRecordRepository<E> repository, ProfileAccess access) {
		this.repository = repository;
		this.access = access;
	}

	/** Set by Spring, so that subclasses' constructors don't each need to pass it on. */
	@Autowired
	void setSupport(RecordSupport support) {
		this.support = support;
	}

	/** The type documents can be attached as, or null if this record can't have attachments. */
	public RecordType recordType() {
		return null;
	}

	/** For callers that have already checked access to the profile. */
	public boolean exists(UUID profileId, UUID id) {
		return repository.findByIdAndProfileId(id, profileId).isPresent();
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
		if (request instanceof HealthRecordRequest health) {
			support.requireSourceDocument(profileId, health.sourceDocumentId());
		}
		return toResponse(repository.saveAndFlush(newRecord(profileId, accountId, request)));
	}

	/**
	 * Creates a record from an approved AI draft item. It is DOCUMENTED, names the document as its
	 * source, records the draft item, and gets the document attached. Validation is the same as
	 * for a record entered by hand.
	 */
	@Transactional
	public UUID createFromDraft(UUID accountId, UUID profileId, Map<String, Object> fields, UUID sourceDocumentId,
			UUID draftItemId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		Map<String, Object> body = new HashMap<>(fields);
		body.put("sourceType", SourceType.DOCUMENTED);
		body.put("sourceDocumentId", sourceDocumentId);
		body.remove("version");
		Q request = support.toRequest(body, requestType());
		support.requireSourceDocument(profileId, sourceDocumentId);
		E record = newRecord(profileId, accountId, request);
		if (record instanceof HealthRecord health) {
			health.linkDraftItem(draftItemId);
		}
		E saved = repository.saveAndFlush(record);
		support.attach(profileId, sourceDocumentId, recordType(), saved.id(), accountId);
		return saved.id();
	}

	@SuppressWarnings("unchecked")
	private Class<Q> requestType() {
		Class<?>[] types = GenericTypeResolver.resolveTypeArguments(getClass(), RecordService.class);
		return (Class<Q>) types[1];
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
		E record = load(profileId, id);
		if (recordType() != null) {
			support.deleteAttachments(recordType(), id);
		}
		repository.delete(record);
	}

	protected E load(UUID profileId, UUID id) {
		return repository.findByIdAndProfileId(id, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
	}

}
