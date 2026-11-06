package com.familyhealth.document;

import java.util.Arrays;
import java.util.Optional;

/**
 * Identifies an upload by its first bytes, never by its name or the browser's claimed type
 * (docs/06 section 5). Only PDF, JPEG, and PNG are allowed.
 */
enum FileSignature {

	PDF("application/pdf", new byte[] { '%', 'P', 'D', 'F', '-' }),
	JPEG("image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }),
	PNG("image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });

	/** Enough bytes to recognise every allowed type. */
	static final int HEADER_LENGTH = 8;

	private final String contentType;

	private final byte[] magic;

	FileSignature(String contentType, byte[] magic) {
		this.contentType = contentType;
		this.magic = magic;
	}

	String contentType() {
		return contentType;
	}

	static Optional<FileSignature> detect(byte[] header) {
		return Arrays.stream(values())
			.filter(signature -> header.length >= signature.magic.length
					&& Arrays.equals(header, 0, signature.magic.length, signature.magic, 0, signature.magic.length))
			.findFirst();
	}

}
