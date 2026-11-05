package com.familyhealth.common;

import org.springframework.http.HttpStatus;

/** A failure the API reports to the client. Its title must never contain health details. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String title) {
		super(title);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
