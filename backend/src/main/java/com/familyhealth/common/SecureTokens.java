package com.familyhealth.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Bearer tokens handed out once. Only the hash is stored, so a database leak reveals no working token. */
public final class SecureTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private SecureTokens() {
	}

	/** 256 bits of secure randomness, safe to place in a URL. */
	public static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
		}
	}

}
