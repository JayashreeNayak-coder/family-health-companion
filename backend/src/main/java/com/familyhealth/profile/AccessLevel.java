package com.familyhealth.profile;

/** Declared lowest first: each level includes the ones before it. */
public enum AccessLevel {

	VIEW,
	EDIT,
	MANAGE;

	boolean includes(AccessLevel required) {
		return compareTo(required) >= 0;
	}

}
