package com.familyhealth.document;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Object storage when STORAGE_BUCKET is set (the hosted demo, DECISIONS.md, D-033), and otherwise a
 * private folder on the server's disk (development, Docker Compose, and tests).
 */
@Configuration(proxyBeanMethods = false)
class DocumentStorageConfig {

	private static final Logger log = LoggerFactory.getLogger(DocumentStorageConfig.class);

	@Bean
	DocumentStorage documentStorage(@Value("${app.documents.s3.bucket:}") String bucket,
			@Value("${app.documents.s3.endpoint:}") String endpoint, @Value("${app.documents.s3.region:auto}") String region,
			@Value("${app.documents.s3.access-key:}") String accessKey,
			@Value("${app.documents.s3.secret-key:}") String secretKey,
			@Value("${app.documents.storage-dir:}") String storageDir) {
		if (bucket == null || bucket.isBlank()) {
			log.info("Documents and photos are stored on this server's disk");
			return new LocalDocumentStorage(storageDir);
		}
		S3DocumentStorage storage = new S3DocumentStorage(endpoint, region, bucket, accessKey, secretKey);
		log.info("Documents and photos are stored in object storage. Checking access in the background");
		// Never delays startup. Says clearly whether the keys and bucket name work (no values are logged).
		Thread.ofVirtual().name("storage-check").start(() -> {
			try {
				storage.checkBucket();
				log.info("Object storage bucket is reachable");
			}
			catch (IOException ex) {
				log.warn("Object storage bucket is not reachable: {}. Check STORAGE_ENDPOINT, STORAGE_BUCKET, "
						+ "STORAGE_ACCESS_KEY, and STORAGE_SECRET_KEY", ex.getMessage());
			}
		});
		return storage;
	}

}
