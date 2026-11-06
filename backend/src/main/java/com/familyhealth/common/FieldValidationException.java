package com.familyhealth.common;

import java.util.List;

/** Field errors found outside request binding, reported like any other validation failure. */
public class FieldValidationException extends RuntimeException {

	public record FieldError(String field, String message) {
	}

	private final List<FieldError> errors;

	public FieldValidationException(List<FieldError> errors) {
		super("Validation failed");
		this.errors = List.copyOf(errors);
	}

	public List<FieldError> errors() {
		return errors;
	}

}
