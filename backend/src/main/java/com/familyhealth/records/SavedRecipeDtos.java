package com.familyhealth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class SavedRecipeDtos {

	static final String AI_GENERATED = "AI_GENERATED";

	private SavedRecipeDtos() {
	}

	/**
	 * A recipe as the meal assistant returned it, possibly edited by the user. {@code mode} and
	 * {@code promptVersion} are kept from creation; an update can't change them.
	 */
	record Request(
			@NotBlank @Size(max = 200) String title,
			@NotEmpty @Size(max = 40) List<@NotBlank @Size(max = 1000) String> ingredients,
			@NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 1000) String> steps,
			@NotNull RecipeMode mode,
			@NotBlank @Pattern(regexp = "recipes-v[0-9]{1,3}") String promptVersion,
			Long version) implements VersionedRequest {
	}

	/** {@code contentType} is always AI_GENERATED, so the UI labels it (docs/06 section 8.3). */
	record Response(UUID id, UUID profileId, String title, List<String> ingredients, List<String> steps,
			RecipeMode mode, String promptVersion, String contentType, Instant createdAt, Long version) {
	}

}
