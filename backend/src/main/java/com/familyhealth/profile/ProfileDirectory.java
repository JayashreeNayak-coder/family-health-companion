package com.familyhealth.profile;

import java.util.UUID;
import com.familyhealth.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A profile's display details for other modules. Callers check access first, through {@link ProfileAccess}. */
@Service
public class ProfileDirectory {

	private final HealthProfileRepository profiles;

	ProfileDirectory(HealthProfileRepository profiles) {
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	public String fullName(UUID profileId) {
		return profiles.findById(profileId)
			.map(HealthProfile::fullName)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
	}

}
