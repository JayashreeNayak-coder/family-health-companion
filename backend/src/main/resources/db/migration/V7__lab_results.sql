-- Phase 9: lab results. See docs/04-database-design.md sections 1.2 and 5.

CREATE TABLE lab_result (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	source_type text NOT NULL,
	source_document_id uuid,
	source_document_deleted_at timestamptz,
	extraction_draft_item_id uuid,
	created_by uuid NOT NULL REFERENCES user_account (id),
	test_name text NOT NULL,
	test_key text NOT NULL,
	value_numeric numeric,
	value_text text,
	unit text,
	result_on date,
	reference_range_as_printed text,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT lab_result_source_type_ck CHECK (source_type IN ('USER_REPORTED', 'DOCUMENTED')),
	CONSTRAINT lab_result_source_ck CHECK (source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL),
	CONSTRAINT lab_result_value_ck CHECK (value_numeric IS NOT NULL OR value_text IS NOT NULL)
);

CREATE INDEX lab_result_profile_idx ON lab_result (profile_id, result_on DESC);
CREATE INDEX lab_result_trend_idx ON lab_result (profile_id, test_key, unit, result_on);
CREATE INDEX lab_result_created_by_idx ON lab_result (created_by);
