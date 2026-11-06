package com.familyhealth.extraction;

import com.familyhealth.ai.AiException;
import com.familyhealth.ai.AiUsageService;
import com.familyhealth.ai.DocumentExtractor;
import com.familyhealth.ai.ExtractionResponseParser;
import com.familyhealth.document.DocumentLookup;
import com.familyhealth.document.ExtractionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Takes queued jobs one at a time. The provider call happens outside any transaction, so a slow
 * provider holds no database locks. Logs carry codes only, never document or response content.
 */
@Component
class ExtractionWorker {

	private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);

	private final ExtractionJobRepository jobs;

	private final ExtractionDraftRepository drafts;

	private final ExtractionDraftItemRepository items;

	private final DocumentLookup documents;

	private final DocumentExtractor extractor;

	private final AiUsageService usage;

	private final TransactionTemplate transaction;

	private final JsonMapper json;

	private final Clock clock;

	private final int maxAttempts;

	private final Duration staleAfter;

	private final boolean enabled;

	ExtractionWorker(ExtractionJobRepository jobs, ExtractionDraftRepository drafts,
			ExtractionDraftItemRepository items, DocumentLookup documents, DocumentExtractor extractor,
			AiUsageService usage, PlatformTransactionManager transactions, JsonMapper json, Clock clock,
			@Value("${app.ai.worker.max-attempts}") int maxAttempts,
			@Value("${app.ai.worker.stale-after}") Duration staleAfter,
			@Value("${app.ai.worker.enabled}") boolean enabled) {
		this.jobs = jobs;
		this.drafts = drafts;
		this.items = items;
		this.documents = documents;
		this.extractor = extractor;
		this.usage = usage;
		this.transaction = new TransactionTemplate(transactions);
		this.json = json;
		this.clock = clock;
		this.maxAttempts = maxAttempts;
		this.staleAfter = staleAfter;
		this.enabled = enabled;
	}

	@Scheduled(fixedDelayString = "${app.ai.worker.poll-interval}")
	void poll() {
		if (!enabled) {
			return;
		}
		try {
			while (runOnce()) {
				// Keep going while there's work.
			}
		}
		catch (RuntimeException ex) {
			log.error("Extraction worker failed", ex);
		}
	}

	/** Processes one queued job, if there is one. Returns whether it did. */
	boolean runOnce() {
		recoverStale();
		Optional<UUID> claimed = transaction.execute(status -> jobs.claimNext().map(job -> {
			job.start(clock.instant());
			documents.setExtractionStatus(job.documentId(), ExtractionStatus.PROCESSING);
			return job.id();
		}));
		if (claimed == null || claimed.isEmpty()) {
			return false;
		}
		process(claimed.get());
		return true;
	}

	private void process(UUID jobId) {
		ExtractionJob job = jobs.findById(jobId).orElse(null);
		if (job == null) {
			return; // The document was deleted meanwhile.
		}
		DocumentExtractor.Result result;
		try {
			Optional<DocumentLookup.StoredFile> file = documents.readContent(job.documentId());
			if (file.isEmpty()) {
				return;
			}
			result = extractor.extract(file.get().content(), file.get().contentType());
		}
		catch (AiException ex) {
			usage.recordCall(job.requestedBy(), ExtractionService.FEATURE, null, null, ex.code().name());
			// The message holds only a status or reason, never document or response content.
			log.warn("Extraction job {} failed with {}: {}", jobId, ex.code(), ex.getMessage());
			finishFailed(jobId, ex.code().name(), ex.code().retryable());
			return;
		}
		catch (RuntimeException ex) {
			log.error("Extraction job {} failed unexpectedly", jobId, ex);
			finishFailed(jobId, "INTERNAL_ERROR", false);
			return;
		}
		usage.recordCall(job.requestedBy(), ExtractionService.FEATURE, result.inputTokens(), result.outputTokens(),
				"SUCCEEDED");
		if (!result.readable()) {
			finishFailed(jobId, "UNREADABLE", false);
			return;
		}
		if (result.items().isEmpty()) {
			finishFailed(jobId, "NOTHING_FOUND", false);
			return;
		}
		saveDraft(jobId, result.items());
	}

	private void saveDraft(UUID jobId, List<DocumentExtractor.Item> found) {
		transaction.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
			ExtractionDraft draft = drafts.save(new ExtractionDraft(job.documentId(), job.id(),
					"extraction-" + ExtractionResponseParser.VERSION));
			for (int i = 0; i < found.size(); i++) {
				DocumentExtractor.Item item = found.get(i);
				items.save(new ExtractionDraftItem(draft.id(), i, item.type(), json.writeValueAsString(item.payload())));
			}
			job.succeed(clock.instant());
			documents.setExtractionStatus(job.documentId(), ExtractionStatus.READY_FOR_REVIEW);
		}));
	}

	/** A retryable failure goes back in the queue until its attempts run out. The original is kept either way. */
	private void finishFailed(UUID jobId, String code, boolean retryable) {
		transaction.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
			if (retryable && job.attempts() < maxAttempts) {
				job.requeue(code);
				documents.setExtractionStatus(job.documentId(), ExtractionStatus.QUEUED);
			}
			else {
				job.fail(code, clock.instant());
				documents.setExtractionStatus(job.documentId(), ExtractionStatus.FAILED);
			}
		}));
	}

	/** A job still processing long after it started was interrupted, for example by a restart. */
	private void recoverStale() {
		Instant cutoff = clock.instant().minus(staleAfter);
		List<UUID> stale = jobs.findByStatusAndStartedAtBefore(ExtractionJob.Status.PROCESSING, cutoff)
			.stream()
			.map(ExtractionJob::id)
			.toList();
		stale.forEach(id -> finishFailed(id, "INTERRUPTED", true));
	}

}
