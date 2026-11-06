package com.familyhealth.document;

import java.io.IOException;
import java.io.InputStream;

/**
 * Private storage for original files, addressed by a random key that contains no personal data.
 * The prototype stores files on the server's disk. An S3-compatible implementation replaces it when
 * hosting is chosen in Phase 3, without changes elsewhere (docs/03 section 10).
 */
public interface DocumentStorage {

	void store(String key, InputStream content) throws IOException;

	InputStream open(String key) throws IOException;

	/** Does nothing if the file is already gone. */
	void delete(String key) throws IOException;

}
