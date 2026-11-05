package com.familyhealth.records;

/** A record's request body. {@code version} is required on update and ignored on create. */
public interface VersionedRequest {

	Long version();

}
