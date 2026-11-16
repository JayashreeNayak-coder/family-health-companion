package com.familyhealth.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.regex.Pattern;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Files in a private S3-compatible bucket, Cloudflare R2 in the demo (DECISIONS.md, D-033). The
 * bucket is never public: every read goes through the backend's access checks. Keys are checked
 * against the format this application generates, as in {@link LocalDocumentStorage}.
 */
class S3DocumentStorage implements DocumentStorage {

	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final S3Client s3;

	private final String bucket;

	S3DocumentStorage(String endpoint, String region, String bucket, String accessKey, String secretKey) {
		requireSet(endpoint, "STORAGE_ENDPOINT");
		requireSet(accessKey, "STORAGE_ACCESS_KEY");
		requireSet(secretKey, "STORAGE_SECRET_KEY");
		this.bucket = bucket;
		this.s3 = S3Client.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.of(region == null || region.isBlank() ? "auto" : region))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			.serviceConfiguration(S3Configuration.builder()
				// The bucket goes in the path, which every S3-compatible service accepts.
				.pathStyleAccessEnabled(true)
				// The body is sent as is, with its length, not in the SDK's signed-chunk format.
				.chunkedEncodingEnabled(false)
				.build())
			// Newer SDKs add checksum trailers by default, which some S3-compatible services reject.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.httpClient(UrlConnectionHttpClient.builder()
				.connectionTimeout(Duration.ofSeconds(10))
				.socketTimeout(Duration.ofSeconds(60))
				.build())
			.build();
	}

	@Override
	public void store(String key, InputStream content) throws IOException {
		checkKey(key);
		// Uploads are at most 10 MB, and S3 needs the length up front.
		byte[] bytes = content.readAllBytes();
		try {
			s3.putObject(put -> put.bucket(bucket).key(key), RequestBody.fromBytes(bytes));
		}
		catch (SdkException ex) {
			throw failure("store", ex);
		}
	}

	@Override
	public InputStream open(String key) throws IOException {
		checkKey(key);
		try {
			return s3.getObject(get -> get.bucket(bucket).key(key));
		}
		catch (NoSuchKeyException ex) {
			// The same exception local storage throws for a missing file.
			throw new NoSuchFileException(key);
		}
		catch (SdkException ex) {
			throw failure("read", ex);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		checkKey(key);
		try {
			// Deleting a missing object succeeds, so a file that is already gone needs no special case.
			s3.deleteObject(delete -> delete.bucket(bucket).key(key));
		}
		catch (SdkException ex) {
			throw failure("delete", ex);
		}
	}

	/** Confirms the bucket can be reached with these keys. Used once at startup, to log a clear message. */
	void checkBucket() throws IOException {
		try {
			s3.headBucket(head -> head.bucket(bucket));
		}
		catch (SdkException ex) {
			throw failure("reach the bucket for", ex);
		}
	}

	/** The status code only: S3 messages can quote the bucket and key. */
	private static IOException failure(String action, SdkException ex) {
		String detail = ex instanceof S3Exception s3Error ? "status " + s3Error.statusCode()
				: ex.getClass().getSimpleName();
		return new IOException("Object storage could not " + action + " a file (" + detail + ")", ex);
	}

	private static void checkKey(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Invalid storage key");
		}
	}

	private static void requireSet(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Set " + name + " when STORAGE_BUCKET is set");
		}
	}

}
