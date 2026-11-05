-- Phase 6: conditions, allergies, dietary restrictions, and emergency contacts, with provenance.
-- See docs/04-database-design.md sections 1.2 and 4. The foreign keys for source_document_id and
-- extraction_draft_item_id arrive with their target tables in Phases 11 and 12.

CREATE TABLE condition (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	name text NOT NULL,
	status text NOT NULL,
	onset_on date,
	notes text,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT condition_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT condition_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT condition_status_ck CHECK (status IN ('ACTIVE', 'RESOLVED', 'UNKNOWN'))
);

CREATE INDEX condition_profile_idx ON condition (profile_id, onset_on DESC);
CREATE INDEX condition_created_by_idx ON condition (created_by);

CREATE TABLE allergy (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	allergen text NOT NULL,
	reaction_as_recorded text,
	severity_as_recorded text,
	notes text,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT allergy_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT allergy_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL)
);

CREATE INDEX allergy_profile_idx ON allergy (profile_id);
CREATE INDEX allergy_created_by_idx ON allergy (created_by);

CREATE TABLE dietary_restriction (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	kind text NOT NULL,
	description text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT dietary_restriction_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT dietary_restriction_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT dietary_restriction_kind_ck CHECK (kind IN ('RESTRICTION', 'PREFERENCE'))
);

CREATE INDEX dietary_restriction_profile_idx ON dietary_restriction (profile_id);
CREATE INDEX dietary_restriction_created_by_idx ON dietary_restriction (created_by);

-- Not a health record, so it has no provenance columns.
CREATE TABLE emergency_contact (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	name text NOT NULL,
	relationship text,
	phone text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL
);

CREATE INDEX emergency_contact_profile_idx ON emergency_contact (profile_id);
