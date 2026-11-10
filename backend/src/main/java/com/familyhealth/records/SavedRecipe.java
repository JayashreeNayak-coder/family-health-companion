package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A meal assistant suggestion the user chose to keep. It stays AI-generated content, not a health
 * record. Ingredients and steps are JSON arrays of text, converted by the service.
 */
@Entity
@Table(name = "saved_recipe")
class SavedRecipe extends ProfileRecord {

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(nullable = false)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String ingredients;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String steps;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private RecipeMode mode;

	@Column(name = "prompt_version", nullable = false, updatable = false)
	private String promptVersion;

	protected SavedRecipe() {
	}

	SavedRecipe(UUID profileId, UUID createdBy, RecipeMode mode, String promptVersion) {
		super(profileId);
		this.createdBy = createdBy;
		this.mode = mode;
		this.promptVersion = promptVersion;
	}

	void update(String title, String ingredientsJson, String stepsJson) {
		this.title = title;
		this.ingredients = ingredientsJson;
		this.steps = stepsJson;
	}

	UUID createdBy() {
		return createdBy;
	}

	String title() {
		return title;
	}

	String ingredientsJson() {
		return ingredients;
	}

	String stepsJson() {
		return steps;
	}

	RecipeMode mode() {
		return mode;
	}

	String promptVersion() {
		return promptVersion;
	}

}
