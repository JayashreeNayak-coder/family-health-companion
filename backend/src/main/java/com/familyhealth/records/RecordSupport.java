package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.document.DocumentLookup;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** What every record service needs from outside its own table. Injected into {@link RecordService}. */
@Component
class RecordSupport {

	private final DocumentLookup documents;

	private final RecordAttachmentRepository attachments;

	RecordSupport(DocumentLookup documents, RecordAttachmentRepository attachments) {
		this.documents = documents;
		this.attachments = attachments;
	}

	/** A source document must belong to the record's own profile. Otherwise it's reported as not found. */
	void requireSourceDocument(UUID profileId, UUID documentId) {
		if (documentId != null && !documents.existsInProfile(profileId, documentId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_DOCUMENT_NOT_FOUND", "Source document not found");
		}
	}

	/** The attached documents stay. Only the links go. */
	void deleteAttachments(RecordType type, UUID recordId) {
		attachments.deleteForRecord(type, recordId);
	}

}
