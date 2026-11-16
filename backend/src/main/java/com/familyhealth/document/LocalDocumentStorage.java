package com.familyhealth.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Files on local disk, in a folder outside anything the web server serves. Keys are checked
 * against the format this application generates, so a key can never reach outside the folder.
 * Chosen by {@link DocumentStorageConfig} when no bucket is set.
 */
class LocalDocumentStorage implements DocumentStorage {

	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final Path root;

	LocalDocumentStorage(String storageDir) {
		if (storageDir == null || storageDir.isBlank()) {
			throw new IllegalStateException("Set DOCUMENT_STORAGE_DIR to a private folder for uploaded documents");
		}
		this.root = Path.of(storageDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Document storage folder can't be created", ex);
		}
	}

	@Override
	public void store(String key, InputStream content) throws IOException {
		Path target = resolve(key);
		Path partial = target.resolveSibling(key + ".part");
		try {
			Files.copy(content, partial, StandardCopyOption.REPLACE_EXISTING);
			Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
		}
		finally {
			Files.deleteIfExists(partial);
		}
	}

	@Override
	public InputStream open(String key) throws IOException {
		return Files.newInputStream(resolve(key));
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	private Path resolve(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return root.resolve(key);
	}

}
