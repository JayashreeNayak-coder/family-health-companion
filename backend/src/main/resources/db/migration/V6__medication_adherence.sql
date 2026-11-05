-- Phase 8: medications, schedules, and adherence. See docs/04-database-design.md section 5.

CREATE TABLE medication (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	name text NOT NULL,
	dosage_as_recorded text,
	instructions_as_recorded text,
	prescribed_by text,
	start_on date,
	end_on date,
	status text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT medication_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT medication_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT medication_status_ck CHECK (status IN ('ACTIVE', 'STOPPED', 'UNKNOWN')),
	CONSTRAINT medication_dates_ck CHECK (start_on IS NULL OR end_on IS NULL OR end_on >= start_on)
);

CREATE INDEX medication_profile_idx ON medication (profile_id, start_on DESC);
CREATE INDEX medication_created_by_idx ON medication (created_by);

CREATE TABLE medication_schedule (
	id uuid PRIMARY KEY,
	medication_id uuid NOT NULL REFERENCES medication (id) ON DELETE CASCADE,
	time_of_day time NOT NULL,
	days_of_week smallint[],
	time_zone text NOT NULL,
	reminder_enabled boolean NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL
);

CREATE INDEX medication_schedule_medication_idx ON medication_schedule (medication_id);

-- Changing a schedule keeps the history it produced, so schedule_id becomes NULL rather than deleting events.
CREATE TABLE adherence_event (
	id uuid PRIMARY KEY,
	medication_id uuid NOT NULL REFERENCES medication (id) ON DELETE CASCADE,
	schedule_id uuid REFERENCES medication_schedule (id) ON DELETE SET NULL,
	scheduled_for timestamptz NOT NULL,
	status text NOT NULL,
	recorded_at timestamptz NOT NULL,
	recorded_by uuid NOT NULL REFERENCES user_account (id),
	CONSTRAINT adherence_event_dose_uk UNIQUE (schedule_id, scheduled_for),
	CONSTRAINT adherence_event_status_ck CHECK (status IN ('TAKEN', 'SKIPPED'))
);

CREATE INDEX adherence_event_medication_idx ON adherence_event (medication_id, scheduled_for DESC);
CREATE INDEX adherence_event_recorded_by_idx ON adherence_event (recorded_by);
