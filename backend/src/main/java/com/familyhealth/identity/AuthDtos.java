package com.familyhealth.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response bodies for /auth. Requests redact the password from toString. */
final class AuthDtos {

	private AuthDtos() {
	}

	record RegisterRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@ValidPassword String password,
			@NotBlank @Size(max = 100) String displayName) {

		@Override
		public String toString() {
			return "RegisterRequest[redacted]";
		}

	}

	/** Deliberately no format or password policy checks, so login reveals nothing about the rules for an account. */
	record LoginRequest(
			@NotBlank @Size(max = 254) String email,
			@NotEmpty @Size(max = 1024) String password) {

		@Override
		public String toString() {
			return "LoginRequest[redacted]";
		}

	}

	record AccountResponse(UUID id, String email, String displayName) {

		static AccountResponse from(UserAccount account) {
			return new AccountResponse(account.id(), account.email(), account.displayName());
		}

	}

	record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
	}

	/** The token comes from the emailed link. */
	record PasswordResetConfirmation(@NotBlank @Size(max = 200) String token, @ValidPassword String newPassword) {
	}

	/** Which ways of signing in are switched on, so the page shows only those. */
	record ProvidersResponse(boolean google) {
	}

	record CsrfResponse(String headerName, String token) {
	}

}
