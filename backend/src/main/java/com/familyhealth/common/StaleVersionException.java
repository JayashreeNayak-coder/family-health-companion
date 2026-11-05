package com.familyhealth.common;

import org.springframework.http.HttpStatus;

public class StaleVersionException extends ApiException {

	public StaleVersionException() {
		super(HttpStatus.CONFLICT, "STALE_VERSION", "This item was changed by someone else. Reload and try again.");
	}

}
