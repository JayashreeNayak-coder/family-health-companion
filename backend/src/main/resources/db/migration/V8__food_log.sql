-- Phase 10: food log. See docs/04-database-design.md section 7.
-- saved_recipe_id arrives with saved_recipe in Phase 14.

CREATE TABLE food_log_entry (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	description text NOT NULL,
	meal_type text,
	occurred_on date,
	occurred_time time,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT food_log_entry_meal_type_ck CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'OTHER')),
	CONSTRAINT food_log_entry_time_needs_date_ck CHECK (occurred_time IS NULL OR occurred_on IS NOT NULL)
);

CREATE INDEX food_log_entry_profile_idx ON food_log_entry (profile_id, occurred_on DESC);
