package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.document.DocumentLookup;
import com.familyhealth.document.DocumentSummary;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links documents to records as evidence, such as a prescription photo on a medication. Both must
 * belong to the same profile. Attaching or detaching never changes the document or the record.
 */
@Service
class AttachmentService {

	record Attachment(UUID id, RecordType recordType, UUID recordId, DocumentSummary document) {
	}

	private final RecordAttachmentRepository attachments;

	private final DocumentLookup documents;

	private final ProfileAccess access;

	private final Map<RecordType, RecordService<?, ?, ?>> records = new EnumMap<>(RecordType.class);

	AttachmentService(RecordAttachmentRepository attachments, DocumentLookup documents, ProfileAccess access,
			List<RecordService<?, ?, ?>> services) {
		this.attachments = attachments;
		this.documents = documents;
		this.access = access;
		services.stream()
			.filter(service -> service.recordType() != null)
			.forEach(service -> records.put(service.recordType(), service));
	}

	/** All of one type's attachments in the profile, or one record's when recordId is given. */
	@Transactional(readOnly = true)
	public List<Attachment> list(UUID accountId, UUID profileId, RecordType type, UUID recordId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		List<RecordAttachment> found = recordId == null
				? attachments.findByProfileIdAndRecordTypeOrderByCreatedAtAscIdAsc(profileId, type)
				: attachments.findByProfileIdAndRecordTypeAndRecordIdOrderByCreatedAtAscIdAsc(profileId, type, recordId);
		Map<UUID, DocumentSummary> summaries = documents.summaries(profileId,
				found.stream().map(RecordAttachment::documentId).distinct().toList());
		return found.stream()
			.map(link -> new Attachment(link.id(), link.recordType(), link.recordId(),
					summaries.get(link.documentId())))
			.filter(attachment -> Objects.nonNull(attachment.document()))
			.toList();
	}

	@Transactional
	public Attachment attach(UUID accountId, UUID profileId, RecordType type, UUID recordId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		RecordService<?, ?, ?> service = records.get(type);
		if (service == null || !service.exists(profileId, recordId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "Record not found");
		}
		DocumentSummary document = documents.summaries(profileId, List.of(documentId)).get(documentId);
		if (document == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found");
		}
		if (attachments.existsByRecordTypeAndRecordIdAndDocumentId(type, recordId, documentId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ATTACHED", "This document is already attached");
		}
		RecordAttachment link = attachments
			.saveAndFlush(new RecordAttachment(profileId, documentId, type, recordId, accountId));
		return new Attachment(link.id(), type, recordId, document);
	}

	/** The document stays in the profile's documents. */
	@Transactional
	public void detach(UUID accountId, UUID profileId, UUID attachmentId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		RecordAttachment link = attachments.findByIdAndProfileId(attachmentId, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
		attachments.delete(link);
	}

}
