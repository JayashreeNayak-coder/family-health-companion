-- Phase 14: saved recipes and food log entries made from them. See docs/04-database-design.md section 7.
-- A saved recipe is AI-generated content the user chose to keep. It is never a health record.

CREATE TABLE saved_recipe (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	created_by uuid NOT NULL REFERENCES user_account (id),
	title text NOT NULL,
	ingredients jsonb NOT NULL,
	steps jsonb NOT NULL,
	mode text NOT NULL,
	prompt_version text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT saved_recipe_mode_ck CHECK (mode IN ('GENERAL', 'HEALTH_AWARE')),
	CONSTRAINT saved_recipe_ingredients_ck CHECK (jsonb_typeof(ingredients) = 'array'),
	CONSTRAINT saved_recipe_steps_ck CHECK (jsonb_typeof(steps) = 'array')
);

CREATE INDEX saved_recipe_profile_idx ON saved_recipe (profile_id, created_at DESC);

-- Deleting a saved recipe keeps the food log entries made from it.
ALTER TABLE food_log_entry ADD COLUMN saved_recipe_id uuid REFERENCES saved_recipe (id) ON DELETE SET NULL;

CREATE INDEX food_log_entry_saved_recipe_idx ON food_log_entry (saved_recipe_id);
