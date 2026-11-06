package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.document.DocumentLookup;
import com.familyhealth.common.FieldValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** What every record service needs from outside its own table. Injected into {@link RecordService}. */
@Component
class RecordSupport {

	private final DocumentLookup documents;

	private final RecordAttachmentRepository attachments;

	private final JsonMapper json;

	private final Validator validator;

	RecordSupport(DocumentLookup documents, RecordAttachmentRepository attachments, JsonMapper json,
			Validator validator) {
		this.documents = documents;
		this.attachments = attachments;
		this.json = json;
		this.validator = validator;
	}

	/**
	 * Builds a record's request from draft fields, and runs the same validation as the API. A
	 * value of the wrong type, such as an unreadable date, is a field error, not a guess.
	 */
	<Q> Q toRequest(Map<String, Object> fields, Class<Q> type) {
		Q request;
		try {
			request = json.convertValue(fields, type);
		}
		catch (JacksonException ex) {
			throw new FieldValidationException(List.of(new FieldValidationException.FieldError(fieldOf(ex),
					"has an invalid value")));
		}
		Set<ConstraintViolation<Q>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new FieldValidationException(violations.stream()
				.map(violation -> new FieldValidationException.FieldError(violation.getPropertyPath().toString(),
						violation.getMessage()))
				.sorted(java.util.Comparator.comparing(FieldValidationException.FieldError::field))
				.toList());
		}
		return request;
	}

	/** The document a confirmed record came from is also attached to it, so the proof is one click away. */
	void attach(UUID profileId, UUID documentId, RecordType type, UUID recordId, UUID accountId) {
		if (type != null && !attachments.existsByRecordTypeAndRecordIdAndDocumentId(type, recordId, documentId)) {
			attachments.save(new RecordAttachment(profileId, documentId, type, recordId, accountId));
		}
	}

	private static String fieldOf(JacksonException ex) {
		return ex.getPath().isEmpty() ? "payload" : String.valueOf(ex.getPath().getLast().getPropertyName());
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
