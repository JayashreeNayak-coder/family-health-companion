package com.familyhealth.identity;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * At least 12 characters and at most 72 bytes in UTF-8, with no composition rules. The upper
 * limit is BCrypt's: it can only use 72 bytes, so a longer password would be silently shortened.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
@interface ValidPassword {

	String message() default "must be 12 to 72 characters (some symbols count as more than one)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidPassword, String> {

		private static final int MIN_CHARACTERS = 12;

		private static final int MAX_BYTES = 72;

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value != null && value.length() >= MIN_CHARACTERS
					&& value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
		}

	}

}
