package com.familyhealth.photo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import com.familyhealth.common.SecureTokens;
import com.familyhealth.document.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos of profiles and families, kept in the same private storage as documents under their own
 * random keys. The owning module stores the key and checks access; this class never does. Call it
 * inside a transaction, so files follow the database: a rolled-back change leaves no new file, and
 * an old file goes only once the change commits.
 */
@Component
public class PhotoStore {

	private static final Logger log = LoggerFactory.getLogger(PhotoStore.class);

	private final DocumentStorage storage;

	PhotoStore(DocumentStorage storage) {
		this.storage = storage;
	}

	/** Validates and re-encodes the upload, stores it, and returns the new key. */
	public String save(MultipartFile file) {
		byte[] upload;
		try {
			upload = file == null ? null : file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The upload couldn't be read", ex);
		}
		byte[] photo = PhotoImages.process(upload);
		String key = SecureTokens.generate();
		try {
			storage.store(key, new ByteArrayInputStream(photo));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The photo couldn't be stored", ex);
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					deleteQuietly(key);
				}
			}
		});
		return key;
	}

	/** Removes the file once the current transaction commits. Does nothing for a null key. */
	public void deleteAfterCommit(String key) {
		if (key == null) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteQuietly(key);
			}
		});
	}

	/** The stored JPEG. Photos are small, so it is read whole. */
	public byte[] read(String key) {
		try (InputStream in = storage.open(key)) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The photo couldn't be read", ex);
		}
	}

	/**
	 * A photo response. Kept only in the viewer's own browser cache, for an hour: the page asks for
	 * a new address (with the change time) whenever the photo changes.
	 */
	public static ResponseEntity<byte[]> response(byte[] photo) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
			.body(photo);
	}

	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("A photo file couldn't be deleted: {}", ex.getClass().getSimpleName());
		}
	}

}
