package com.familyhealth.extraction;

import com.familyhealth.ai.AiUsageService;
import com.familyhealth.ai.DocumentExtractor;
import com.familyhealth.ai.ExtractionResponseParser;
import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.FieldValidationException;
import com.familyhealth.document.DocumentLookup;
import com.familyhealth.document.ExtractionStatus;
import com.familyhealth.extraction.ExtractionDtos.DraftResponse;
import com.familyhealth.extraction.ExtractionDtos.ItemResponse;
import com.familyhealth.extraction.ExtractionDtos.JobResponse;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.RecordService;
import com.familyhealth.records.RecordType;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Queues extraction and runs the review. A draft item becomes a record only through
 * {@link #approve}, which uses the record type's own validation. Rejecting creates nothing.
 */
@Service
class ExtractionService {

	static final String FEATURE = "DOCUMENT_EXTRACTION";

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private final ExtractionJobRepository jobs;

	private final ExtractionDraftRepository drafts;

	private final ExtractionDraftItemRepository items;

	private final DocumentLookup documents;

	private final DocumentExtractor extractor;

	private final AiUsageService usage;

	private final ProfileAccess access;

	private final AuditService audit;

	private final JsonMapper json;

	private final Clock clock;

	private final Map<RecordType, RecordService<?, ?, ?>> records = new EnumMap<>(RecordType.class);

	ExtractionService(ExtractionJobRepository jobs, ExtractionDraftRepository drafts,
			ExtractionDraftItemRepository items, DocumentLookup documents, DocumentExtractor extractor,
			AiUsageService usage, ProfileAccess access, AuditService audit, JsonMapper json, Clock clock,
			List<RecordService<?, ?, ?>> services) {
		this.jobs = jobs;
		this.drafts = drafts;
		this.items = items;
		this.documents = documents;
		this.extractor = extractor;
		this.usage = usage;
		this.access = access;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
		services.stream()
			.filter(service -> service.recordType() != null)
			.forEach(service -> records.put(service.recordType(), service));
	}

	@Transactional
	public JobResponse request(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		requireDocument(profileId, documentId);
		if (!extractor.isAvailable()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
					"AI reading isn't available right now. You can still enter the details by hand.");
		}
		if (jobs.existsByDocumentIdAndStatusIn(documentId,
				List.of(ExtractionJob.Status.QUEUED, ExtractionJob.Status.PROCESSING))) {
			throw new ApiException(HttpStatus.CONFLICT, "EXTRACTION_IN_PROGRESS", "This document is already being read");
		}
		if (drafts.existsByDocumentIdAndStatus(documentId, ExtractionDraft.Status.PENDING_REVIEW)) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAFT_PENDING_REVIEW",
					"Review the existing draft for this document first");
		}
		usage.claim(accountId, FEATURE);
		ExtractionJob job = jobs.save(new ExtractionJob(documentId, accountId, extractor.provider(), extractor.model(),
				extractor.promptVersion()));
		documents.setExtractionStatus(documentId, ExtractionStatus.QUEUED);
		audit.recordForProfile(AuditAction.EXTRACTION_REQUESTED, accountId, profileId,
				Map.of("documentId", documentId.toString()));
		return JobResponse.from(job);
	}

	@Transactional(readOnly = true)
	public JobResponse job(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		requireDocument(profileId, documentId);
		return jobs.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
			.map(JobResponse::from)
			.orElseThrow(ExtractionService::notFound);
	}

	/** The latest draft for the document, whatever its state. */
	@Transactional(readOnly = true)
	public DraftResponse draft(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		requireDocument(profileId, documentId);
		ExtractionDraft draft = drafts.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
			.orElseThrow(ExtractionService::notFound);
		return toResponse(draft);
	}

	@Transactional
	public ItemResponse correct(UUID accountId, UUID profileId, UUID documentId, UUID itemId,
			Map<String, Object> payload, Long version) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		ExtractionDraftItem item = pendingItem(profileId, documentId, itemId, version);
		item.correct(json.writeValueAsString(clean(item.itemType(), payload)));
		return toResponse(items.saveAndFlush(item));
	}

	@Transactional
	public ItemResponse approve(UUID accountId, UUID profileId, UUID documentId, UUID itemId,
			Map<String, Object> payload, Long version) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		ExtractionDraftItem item = pendingItem(profileId, documentId, itemId, version);
		if (payload != null) {
			item.correct(json.writeValueAsString(clean(item.itemType(), payload)));
		}
		RecordService<?, ?, ?> service = records.get(RecordType.valueOf(item.itemType()));
		UUID recordId = service.createFromDraft(accountId, profileId, readPayload(item), documentId, item.id());
		item.approve(recordId, accountId, clock.instant());
		items.saveAndFlush(item);
		finishIfDecided(item.draftId(), accountId);
		audit.recordForProfile(AuditAction.DRAFT_ITEM_APPROVED, accountId, profileId,
				Map.of("documentId", documentId.toString(), "itemType", item.itemType()));
		return toResponse(item);
	}

	@Transactional
	public ItemResponse reject(UUID accountId, UUID profileId, UUID documentId, UUID itemId, Long version) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		ExtractionDraftItem item = pendingItem(profileId, documentId, itemId, version);
		item.reject(accountId, clock.instant());
		items.saveAndFlush(item);
		finishIfDecided(item.draftId(), accountId);
		audit.recordForProfile(AuditAction.DRAFT_ITEM_REJECTED, accountId, profileId,
				Map.of("documentId", documentId.toString(), "itemType", item.itemType()));
		return toResponse(item);
	}

	/** Discards every item still pending. Approved items stay approved. */
	@Transactional
	public DraftResponse rejectRemaining(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		ExtractionDraft draft = pendingDraft(profileId, documentId);
		for (ExtractionDraftItem item : items.findByDraftIdOrderByPosition(draft.id())) {
			if (item.status() == ExtractionDraftItem.Status.PENDING) {
				item.reject(accountId, clock.instant());
				audit.recordForProfile(AuditAction.DRAFT_ITEM_REJECTED, accountId, profileId,
						Map.of("documentId", documentId.toString(), "itemType", item.itemType()));
			}
		}
		items.flush();
		finishIfDecided(draft.id(), accountId);
		return toResponse(draft);
	}

	private void finishIfDecided(UUID draftId, UUID accountId) {
		List<ExtractionDraftItem> all = items.findByDraftIdOrderByPosition(draftId);
		if (all.stream().anyMatch(item -> item.status() == ExtractionDraftItem.Status.PENDING)) {
			return;
		}
		ExtractionDraft draft = drafts.findById(draftId).orElseThrow();
		draft.finish(all.stream().anyMatch(item -> item.status() == ExtractionDraftItem.Status.APPROVED), accountId,
				clock.instant());
		drafts.saveAndFlush(draft);
		documents.setExtractionStatus(draft.documentId(), ExtractionStatus.REVIEWED);
	}

	private ExtractionDraftItem pendingItem(UUID profileId, UUID documentId, UUID itemId, Long version) {
		ExtractionDraft draft = pendingDraft(profileId, documentId);
		ExtractionDraftItem item = items.findByIdAndDraftId(itemId, draft.id()).orElseThrow(ExtractionService::notFound);
		if (item.status() != ExtractionDraftItem.Status.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "ITEM_ALREADY_DECIDED", "This item was already approved or rejected");
		}
		item.requireVersion(version);
		return item;
	}

	private ExtractionDraft pendingDraft(UUID profileId, UUID documentId) {
		requireDocument(profileId, documentId);
		ExtractionDraft draft = drafts.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
			.orElseThrow(ExtractionService::notFound);
		if (draft.status() != ExtractionDraft.Status.PENDING_REVIEW) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAFT_NOT_PENDING", "This draft has already been reviewed");
		}
		return draft;
	}

	/** Only the type's own fields are kept. A blank value is unknown, so it's stored as null. */
	private static Map<String, Object> clean(String type, Map<String, Object> payload) {
		List<String> allowed = ExtractionResponseParser.fieldsFor(type);
		List<FieldValidationException.FieldError> unknown = payload.keySet()
			.stream()
			.filter(key -> !allowed.contains(key))
			.sorted()
			.map(key -> new FieldValidationException.FieldError(key, "is not a field of this item"))
			.toList();
		if (!unknown.isEmpty()) {
			throw new FieldValidationException(unknown);
		}
		Map<String, Object> cleaned = new LinkedHashMap<>();
		for (String field : allowed) {
			Object value = payload.get(field);
			cleaned.put(field, value instanceof String text && text.isBlank() ? null : value);
		}
		return cleaned;
	}

	private void requireDocument(UUID profileId, UUID documentId) {
		if (documents.find(profileId, documentId).isEmpty()) {
			throw notFound();
		}
	}

	private Map<String, Object> readPayload(ExtractionDraftItem item) {
		return json.readValue(item.payload(), MAP);
	}

	private ItemResponse toResponse(ExtractionDraftItem item) {
		return new ItemResponse(item.id(), item.itemType(), readPayload(item), item.status().name(),
				item.confirmedRecordId(), item.version());
	}

	private DraftResponse toResponse(ExtractionDraft draft) {
		List<ItemResponse> found = items.findByDraftIdOrderByPosition(draft.id()).stream().map(this::toResponse).toList();
		return new DraftResponse(draft.id(), draft.documentId(), draft.status().name(), draft.schemaVersion(),
				ExtractionDtos.AI_GENERATED, ExtractionDtos.DISCLAIMER,
				List.of("Values the document doesn't show are left empty, never guessed.",
						"Scans and X-ray images are not interpreted."),
				found);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
	}

}
