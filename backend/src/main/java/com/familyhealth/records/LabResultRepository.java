package com.familyhealth.records;

import java.util.List;
import java.util.UUID;

interface LabResultRepository extends ProfileRecordRepository<LabResult> {

	List<LabResult> findByProfileIdAndTestKey(UUID profileId, String testKey);

}
