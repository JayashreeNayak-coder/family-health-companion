package com.familyhealth.document;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.common.SecureTokens;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.profile.ProfileFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores originals and serves them only through an access check (docs/06 section 5). The system
 * never alters or deletes a stored file on its own: only an explicit delete removes one.
 */
@Service
class DocumentService implements DocumentLookup, ProfileFiles {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private static final int MAX_FILENAME_LENGTH = 200;

	record Content(DocumentSummary summary, InputStream stream) {
	}

	private final DocumentRepository documents;

	private final DocumentStorage storage;

	private final DocumentReferences references;

	private final ProfileAccess access;

	private final AuditService audit;

	private final long maxBytes;

	DocumentService(DocumentRepository documents, DocumentStorage storage, DocumentReferences references,
			ProfileAccess access, AuditService audit, @Value("${app.documents.max-file-size}") DataSize maxFileSize) {
		this.documents = documents;
		this.storage = storage;
		this.references = references;
		this.access = access;
		this.audit = audit;
		this.maxBytes = maxFileSize.toBytes();
	}

	@Transactional
	public DocumentSummary upload(UUID accountId, UUID profileId, MultipartFile file, DocumentCategory category,
			LocalDate documentOn, String description) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload");
		}
		if (file.getSize() > maxBytes) {
			throw fileTooLarge();
		}
		FileSignature signature = FileSignature.detect(header(file)).orElseThrow(() -> new ApiException(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Only PDF, JPEG, and PNG files are allowed"));

		String key = SecureTokens.generate();
		String sha256 = store(key, file);
		deleteFileIfRolledBack(key);

		Document document = new Document(profileId, accountId, cleanFilename(file.getOriginalFilename()),
				signature.contentType(), file.getSize(), sha256, key);
		document.describe(category != null ? category : DocumentCategory.OTHER, documentOn, blankToNull(description));
		documents.saveAndFlush(document);
		audit.recordForProfile(AuditAction.DOCUMENT_UPLOADED, accountId, profileId,
				Map.of("documentId", document.id().toString()));
		return DocumentSummary.from(document);
	}

	@Transactional(readOnly = true)
	public PageResponse<DocumentSummary> list(UUID accountId, UUID profileId, Pageable pageable) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return PageResponse.from(documents.findByProfileId(profileId, pageable), DocumentSummary::from);
	}

	@Transactional(readOnly = true)
	public DocumentSummary get(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		return DocumentSummary.from(load(profileId, documentId));
	}

	/** The caller closes the stream. Each download is audited. */
	@Transactional
	public Content open(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		Document document = load(profileId, documentId);
		InputStream stream;
		try {
			stream = storage.open(document.storageKey());
		}
		catch (IOException ex) {
			log.error("Stored file for a document can't be read", ex);
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UNAVAILABLE",
					"The file can't be read right now");
		}
		audit.recordForProfile(AuditAction.DOCUMENT_DOWNLOADED, accountId, profileId,
				Map.of("documentId", documentId.toString()));
		return new Content(DocumentSummary.from(document), stream);
	}

	/**
	 * Records confirmed from the document keep their data and show that the source was deleted.
	 * Attachments to the document go with it. The file is removed once this commits.
	 */
	@Transactional
	public void delete(UUID accountId, UUID profileId, UUID documentId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		Document document = load(profileId, documentId);
		references.documentDeleted(profileId, documentId);
		documents.delete(document);
		documents.flush();
		deleteFilesAfterCommit(List.of(document.storageKey()));
		audit.recordForProfile(AuditAction.DOCUMENT_DELETED, accountId, profileId,
				Map.of("documentId", documentId.toString()));
	}

	@Override
	public boolean existsInProfile(UUID profileId, UUID documentId) {
		return documents.existsByIdAndProfileId(documentId, profileId);
	}

	@Override
	public Map<UUID, DocumentSummary> summaries(UUID profileId, Collection<UUID> documentIds) {
		if (documentIds.isEmpty()) {
			return Map.of();
		}
		return documents.findByProfileIdAndIdIn(profileId, documentIds)
			.stream()
			.map(DocumentSummary::from)
			.collect(Collectors.toMap(DocumentSummary::id, Function.identity()));
	}

	@Override
	public Optional<DocumentSummary> find(UUID profileId, UUID documentId) {
		return documents.findByIdAndProfileId(documentId, profileId).map(DocumentSummary::from);
	}

	@Override
	public Optional<StoredFile> readContent(UUID documentId) {
		Optional<Document> document = documents.findById(documentId);
		if (document.isEmpty()) {
			return Optional.empty();
		}
		try (InputStream input = storage.open(document.get().storageKey())) {
			return Optional.of(new StoredFile(input.readAllBytes(), document.get().contentType()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("A stored file can't be read", ex);
		}
	}

	@Override
	@Transactional
	public void setExtractionStatus(UUID documentId, ExtractionStatus status) {
		documents.findById(documentId).ifPresent(document -> document.extractionStatus(status));
	}

	@Override
	public void deleteAllAfterCommit(UUID profileId) {
		deleteFilesAfterCommit(documents.findStorageKeysByProfileId(profileId));
	}

	private Document load(UUID profileId, UUID documentId) {
		return documents.findByIdAndProfileId(documentId, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
	}

	private String store(String key, MultipartFile file) {
		try (InputStream input = file.getInputStream();
				DigestInputStream digesting = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
			storage.store(key, digesting);
			return HexFormat.of().formatHex(digesting.getMessageDigest().digest());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The upload couldn't be stored", ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
		}
	}

	private static byte[] header(MultipartFile file) {
		try (InputStream input = file.getInputStream()) {
			return input.readNBytes(FileSignature.HEADER_LENGTH);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The upload couldn't be read", ex);
		}
	}

	/** A failed upload leaves no file behind. */
	private void deleteFileIfRolledBack(String key) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deleteQuietly(key);
				}
			}
		});
	}

	private void deleteFilesAfterCommit(List<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				keys.forEach(DocumentService.this::deleteQuietly);
			}
		});
	}

	/** Storage keys hold no personal data, but there's no need to log them either. */
	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("A stored file couldn't be deleted and is now orphaned", ex);
		}
	}

	/** Keeps the name only, without any folder, control characters, or excess length. */
	static String cleanFilename(String original) {
		String name = original == null ? "" : original;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
		if (name.length() > MAX_FILENAME_LENGTH) {
			name = name.substring(name.length() - MAX_FILENAME_LENGTH);
		}
		return name.isEmpty() ? "document" : name;
	}

	static ApiException fileTooLarge() {
		return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "The file is larger than 10 MB");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
