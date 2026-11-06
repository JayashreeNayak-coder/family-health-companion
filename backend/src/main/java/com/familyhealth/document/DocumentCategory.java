package com.familyhealth.document;

public enum DocumentCategory {

	LAB_REPORT,
	PRESCRIPTION,
	/** An image such as an X-ray. Stored as evidence only: the system never interprets it. */
	SCAN,
	BILL,
	PHOTO,
	OTHER

}
