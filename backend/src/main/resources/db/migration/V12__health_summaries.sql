-- Phase 16: record-based summaries. See docs/04-database-design.md section 7.
-- content keeps a copy of every record the summary cites, so it reads the same after records change.

CREATE TABLE health_summary (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	generated_by uuid NOT NULL REFERENCES user_account (id),
	period_from date,
	period_to date,
	content jsonb NOT NULL,
	source_refs jsonb NOT NULL,
	provider text NOT NULL,
	model text NOT NULL,
	prompt_version text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT health_summary_period_ck CHECK (period_from IS NULL OR period_to IS NULL OR period_from <= period_to),
	CONSTRAINT health_summary_source_refs_ck CHECK (jsonb_typeof(source_refs) = 'array')
);

CREATE INDEX health_summary_profile_idx ON health_summary (profile_id, created_at DESC);
