package com.familyhealth.emergencycard;

/** What an emergency card line is. PERSON and NOTE are typed by the user and never come from a record. */
public enum CardItemType {

	PERSON(false),
	ALLERGY(true),
	CONDITION(true),
	MEDICATION(true),
	EMERGENCY_CONTACT(true),
	NOTE(false);

	private final boolean canHaveSource;

	CardItemType(boolean canHaveSource) {
		this.canHaveSource = canHaveSource;
	}

	public boolean canHaveSource() {
		return canHaveSource;
	}

}
