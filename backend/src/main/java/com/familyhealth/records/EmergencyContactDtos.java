package com.familyhealth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

final class EmergencyContactDtos {

	private EmergencyContactDtos() {
	}

	record Request(
			@NotBlank @Size(max = 200) String name,
			@Size(max = 100) String relationship,
			@NotBlank @Size(max = 30) @Pattern(regexp = "[0-9+() .\\-]+", message = "must be a phone number") String phone,
			Long version) implements VersionedRequest {
	}

	record Response(UUID id, UUID profileId, String name, String relationship, String phone, Long version) {
	}

}
