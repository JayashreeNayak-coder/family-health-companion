package com.familyhealth.records;

/** The records a document can be attached to. Emergency contacts aren't, because they aren't health data. */
public enum RecordType {

	CONDITION,
	ALLERGY,
	DIETARY_RESTRICTION,
	VISIT,
	SYMPTOM,
	MEDICATION,
	LAB_RESULT,
	FOOD_LOG

}
