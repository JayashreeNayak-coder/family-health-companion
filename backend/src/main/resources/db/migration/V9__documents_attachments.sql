-- Phase 11: uploaded documents and record attachments. See docs/04-database-design.md sections 1.2 and 6.
-- The file itself lives in private storage under storage_key. This table holds only its metadata.

CREATE TABLE document (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	uploaded_by uuid NOT NULL REFERENCES user_account (id),
	original_filename text NOT NULL,
	content_type text NOT NULL,
	size_bytes bigint NOT NULL,
	sha256 text NOT NULL,
	storage_key text NOT NULL UNIQUE,
	category text NOT NULL,
	document_on date,
	description text,
	extraction_status text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT document_content_type_ck CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
	CONSTRAINT document_size_ck CHECK (size_bytes > 0),
	CONSTRAINT document_category_ck CHECK (category IN ('LAB_REPORT', 'PRESCRIPTION', 'SCAN', 'BILL', 'PHOTO', 'OTHER')),
	CONSTRAINT document_extraction_status_ck CHECK (extraction_status IN ('NOT_REQUESTED', 'QUEUED', 'PROCESSING', 'READY_FOR_REVIEW', 'FAILED', 'REVIEWED'))
);

CREATE INDEX document_profile_idx ON document (profile_id, created_at DESC);
CREATE INDEX document_uploaded_by_idx ON document (uploaded_by);

-- The provenance foreign keys promised in docs/04 section 9. NO ACTION is checked at the end of the
-- statement, so deleting a profile still cascades to both sides. Deleting one document clears these
-- references first, in the same transaction.
ALTER TABLE condition ADD CONSTRAINT condition_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE allergy ADD CONSTRAINT allergy_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE dietary_restriction ADD CONSTRAINT dietary_restriction_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE doctor_visit ADD CONSTRAINT doctor_visit_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE symptom_entry ADD CONSTRAINT symptom_entry_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE medication ADD CONSTRAINT medication_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);
ALTER TABLE lab_result ADD CONSTRAINT lab_result_source_document_fk FOREIGN KEY (source_document_id) REFERENCES document (id);

CREATE INDEX condition_source_document_idx ON condition (source_document_id);
CREATE INDEX allergy_source_document_idx ON allergy (source_document_id);
CREATE INDEX dietary_restriction_source_document_idx ON dietary_restriction (source_document_id);
CREATE INDEX doctor_visit_source_document_idx ON doctor_visit (source_document_id);
CREATE INDEX symptom_entry_source_document_idx ON symptom_entry (source_document_id);
CREATE INDEX medication_source_document_idx ON medication (source_document_id);
CREATE INDEX lab_result_source_document_idx ON lab_result (source_document_id);

-- A document attached to a record as supporting evidence, such as a prescription photo on a visit.
-- record_id points into the table named by record_type, so it can't be a foreign key. The service
-- checks the record exists in the same profile, and deletes its attachments when it deletes the record.
-- Deleting the document removes its attachments, never the record.
CREATE TABLE record_attachment (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	document_id uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
	record_type text NOT NULL,
	record_id uuid NOT NULL,
	created_by uuid NOT NULL REFERENCES user_account (id),
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT record_attachment_record_type_ck CHECK (record_type IN ('CONDITION', 'ALLERGY', 'DIETARY_RESTRICTION', 'VISIT', 'SYMPTOM', 'MEDICATION', 'LAB_RESULT', 'FOOD_LOG')),
	CONSTRAINT record_attachment_uk UNIQUE (record_type, record_id, document_id)
);

CREATE INDEX record_attachment_profile_idx ON record_attachment (profile_id, record_type);
CREATE INDEX record_attachment_document_idx ON record_attachment (document_id);
CREATE INDEX record_attachment_created_by_idx ON record_attachment (created_by);
