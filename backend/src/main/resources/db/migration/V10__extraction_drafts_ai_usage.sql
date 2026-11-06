-- Phase 12: AI extraction as a reviewable draft. See docs/04-database-design.md section 6.
-- Draft content stays out of the record tables until a person approves each item.

-- Also the work queue: the worker claims QUEUED rows with FOR UPDATE SKIP LOCKED.
CREATE TABLE extraction_job (
	id uuid PRIMARY KEY,
	document_id uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
	requested_by uuid NOT NULL REFERENCES user_account (id),
	status text NOT NULL,
	attempts integer NOT NULL,
	provider text NOT NULL,
	model text NOT NULL,
	prompt_version text NOT NULL,
	error_code text,
	started_at timestamptz,
	finished_at timestamptz,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT extraction_job_status_ck CHECK (status IN ('QUEUED', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX extraction_job_queue_idx ON extraction_job (status, created_at);
CREATE INDEX extraction_job_document_idx ON extraction_job (document_id, created_at DESC);
CREATE INDEX extraction_job_requested_by_idx ON extraction_job (requested_by);

CREATE TABLE extraction_draft (
	id uuid PRIMARY KEY,
	document_id uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
	job_id uuid NOT NULL REFERENCES extraction_job (id) ON DELETE CASCADE,
	schema_version text NOT NULL,
	status text NOT NULL,
	reviewed_by uuid REFERENCES user_account (id),
	reviewed_at timestamptz,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT extraction_draft_status_ck CHECK (status IN ('PENDING_REVIEW', 'COMPLETED', 'REJECTED'))
);

-- One draft awaiting review per document.
CREATE UNIQUE INDEX extraction_draft_pending_uk ON extraction_draft (document_id) WHERE status = 'PENDING_REVIEW';
CREATE INDEX extraction_draft_job_idx ON extraction_draft (job_id);
CREATE INDEX extraction_draft_reviewed_by_idx ON extraction_draft (reviewed_by);

-- payload holds the record's own request fields, so approval runs the record's normal validation.
CREATE TABLE extraction_draft_item (
	id uuid PRIMARY KEY,
	draft_id uuid NOT NULL REFERENCES extraction_draft (id) ON DELETE CASCADE,
	position integer NOT NULL,
	item_type text NOT NULL,
	payload jsonb NOT NULL,
	status text NOT NULL,
	confirmed_record_id uuid,
	decided_by uuid REFERENCES user_account (id),
	decided_at timestamptz,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT extraction_draft_item_type_ck CHECK (item_type IN ('MEDICATION', 'LAB_RESULT', 'CONDITION', 'ALLERGY', 'VISIT')),
	CONSTRAINT extraction_draft_item_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
	CONSTRAINT extraction_draft_item_confirmed_ck CHECK ((status = 'APPROVED') = (confirmed_record_id IS NOT NULL)),
	CONSTRAINT extraction_draft_item_position_uk UNIQUE (draft_id, position)
);

CREATE INDEX extraction_draft_item_decided_by_idx ON extraction_draft_item (decided_by);

-- Token counts and outcomes only. Never prompt or response text.
CREATE TABLE ai_usage_event (
	id uuid PRIMARY KEY,
	user_account_id uuid NOT NULL REFERENCES user_account (id),
	feature text NOT NULL,
	occurred_at timestamptz NOT NULL,
	input_tokens integer,
	output_tokens integer,
	outcome text NOT NULL
);

CREATE INDEX ai_usage_event_account_idx ON ai_usage_event (user_account_id, feature, occurred_at);
CREATE INDEX ai_usage_event_feature_idx ON ai_usage_event (feature, occurred_at);

-- The provenance foreign keys promised in docs/04 section 9. Deleting the document deletes its
-- drafts, and the confirmed records keep their data.
ALTER TABLE condition ADD CONSTRAINT condition_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE allergy ADD CONSTRAINT allergy_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE dietary_restriction ADD CONSTRAINT dietary_restriction_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE doctor_visit ADD CONSTRAINT doctor_visit_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE symptom_entry ADD CONSTRAINT symptom_entry_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE medication ADD CONSTRAINT medication_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;
ALTER TABLE lab_result ADD CONSTRAINT lab_result_draft_item_fk FOREIGN KEY (extraction_draft_item_id) REFERENCES extraction_draft_item (id) ON DELETE SET NULL;

CREATE INDEX condition_draft_item_idx ON condition (extraction_draft_item_id);
CREATE INDEX allergy_draft_item_idx ON allergy (extraction_draft_item_id);
CREATE INDEX dietary_restriction_draft_item_idx ON dietary_restriction (extraction_draft_item_id);
CREATE INDEX doctor_visit_draft_item_idx ON doctor_visit (extraction_draft_item_id);
CREATE INDEX symptom_entry_draft_item_idx ON symptom_entry (extraction_draft_item_id);
CREATE INDEX medication_draft_item_idx ON medication (extraction_draft_item_id);
CREATE INDEX lab_result_draft_item_idx ON lab_result (extraction_draft_item_id);
