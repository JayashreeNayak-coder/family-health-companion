-- Phase 7: doctor visits and symptoms. See docs/04-database-design.md sections 1.1, 1.2, and 4.

CREATE TABLE doctor_visit (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	occurred_on date,
	occurred_time time,
	doctor_name text,
	facility text,
	reason text,
	notes text,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT doctor_visit_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT doctor_visit_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT doctor_visit_time_needs_date_ck CHECK (occurred_time IS NULL OR occurred_on IS NOT NULL)
);

CREATE INDEX doctor_visit_profile_idx ON doctor_visit (profile_id, occurred_on DESC);
CREATE INDEX doctor_visit_created_by_idx ON doctor_visit (created_by);

CREATE TABLE symptom_entry (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	description text NOT NULL,
	severity_as_reported text,
	occurred_on date,
	occurred_time time,
	notes text,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT symptom_entry_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT symptom_entry_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT symptom_entry_time_needs_date_ck CHECK (occurred_time IS NULL OR occurred_on IS NOT NULL)
);

CREATE INDEX symptom_entry_profile_idx ON symptom_entry (profile_id, occurred_on DESC);
CREATE INDEX symptom_entry_created_by_idx ON symptom_entry (created_by);
