# Family Health Companion
## Database Design — v1.0

**Status:** Approved baseline. Written from the problem statement, SRS, and system design; the owner delegated final content (`DECISIONS.md`, D-015). Change it through the normal approval process.  
**Implementation state:** See `PROJECT_STATUS.md`. A described item may not be built yet.  
**Based on:** `01-problem-statement.md`, `02-srs.md` (MVP v0.3), `03-system-design.md` (v0.2), `CLAUDE.md`.

## 1. Conventions

| Topic | Rule |
|---|---|
| Database | PostgreSQL 18 |
| Schema changes | Flyway migrations only. Hibernate runs with `ddl-auto=validate` and never changes the schema. |
| Destructive migrations | Require explicit approval (`CLAUDE.md` section 5) |
| Primary keys | `uuid`, so record IDs in URLs cannot be guessed or counted |
| Naming | `snake_case`, singular table names |
| Audit columns | `created_at`, `updated_at` as `timestamptz NOT NULL` on every mutable table |
| Concurrency | `version bigint` on tables edited by users (JPA optimistic locking) |
| Enumerations | `text` with a `CHECK` constraint, mapped with `@Enumerated(EnumType.STRING)` |
| Foreign keys | Always declared and indexed |
| Dosages and results | Stored as recorded. Never normalized in a way that changes the meaning. |

### 1.1 Dates on health events

The SRS requires date and time "where available", so a health event may have only a date, or no date at all.

- `occurred_on date NULL` and `occurred_time time NULL` hold the date and time as the user recorded them, without time zone conversion.
- `timestamptz` is used only for system events such as `created_at`.
- Records with no date appear in a labelled "Date unknown" group on the timeline. The system never substitutes the upload or entry date.

### 1.2 Record provenance

Every confirmed health record carries:

| Column | Meaning |
|---|---|
| `source_type` | `USER_REPORTED` or `DOCUMENTED` |
| `source_document_id` | Nullable FK to `document`. Required when `source_type = 'DOCUMENTED'`. |
| `source_document_deleted_at` | Nullable `timestamptz`, set when the user deletes the source document |
| `extraction_draft_item_id` | Nullable FK, set when the record came from an approved AI draft |
| `created_by` | The account that entered or approved the record |

The rule is enforced with a `CHECK` constraint:
`source_type <> 'DOCUMENTED' OR source_document_id IS NOT NULL OR source_document_deleted_at IS NOT NULL`.

`DOCUMENTED` means "taken from an attached document". It does not mean the document was verified, because verification is excluded by SRS section 8. `AI_GENERATED` is deliberately not a value here: AI-generated content lives in `extraction_draft_item` until approved, and in `health_summary` and `saved_recipe`, which are labelled as AI output.

## 2. Ownership model

Health profiles are separate from login accounts (`CLAUDE.md` section 5).

```mermaid
erDiagram
    USER_ACCOUNT ||--o{ FAMILY_MEMBERSHIP : "belongs through"
    FAMILY ||--o{ FAMILY_MEMBERSHIP : has
    FAMILY ||--o{ HEALTH_PROFILE : contains
    USER_ACCOUNT |o--o{ HEALTH_PROFILE : "may be linked to"
    USER_ACCOUNT ||--o{ PROFILE_ACCESS_GRANT : receives
    HEALTH_PROFILE ||--o{ PROFILE_ACCESS_GRANT : "is shared through"
    HEALTH_PROFILE ||--o{ HEALTH_RECORDS : owns
    HEALTH_PROFILE ||--o{ DOCUMENT : owns
    DOCUMENT |o--o{ HEALTH_RECORDS : "is the source of"
```

Every profile-owned table has `profile_id uuid NOT NULL` referencing `health_profile`, except direct children of a profile-owned table, which inherit ownership through their parent. Those are listed explicitly below.

## 3. Identity and family

### `user_account`
| Column | Type | Notes |
|---|---|---|
| `id` | uuid | PK |
| `email` | text | NOT NULL, UNIQUE, stored lowercase |
| `password_hash` | text | NOT NULL |
| `display_name` | text | NOT NULL |
| `status` | text | `ACTIVE`, `DISABLED` |

### `family`
| Column | Type | Notes |
|---|---|---|
| `id` | uuid | PK |
| `name` | text | NOT NULL |
| `created_by` | uuid | FK `user_account` |

### `family_membership`
| Column | Type | Notes |
|---|---|---|
| `family_id` | uuid | FK, part of unique key |
| `user_account_id` | uuid | FK, part of unique key |
| `role` | text | `MANAGER`, `MEMBER` |

### `health_profile`
| Column | Type | Notes |
|---|---|---|
| `id` | uuid | PK |
| `family_id` | uuid | FK, NOT NULL, indexed |
| `linked_account_id` | uuid | FK `user_account`, NULL for a dependent. The person's own account when one exists. |
| `full_name` | text | NOT NULL |
| `date_of_birth` | date | NULL |
| `is_dependent` | boolean | NOT NULL |

Unique: (`family_id`, `linked_account_id`). Sex, blood group, and government or insurance identifiers are deliberately not collected, because no MVP feature requires them (see Q1).

### `profile_access_grant`
| Column | Type | Notes |
|---|---|---|
| `profile_id` | uuid | FK, part of unique key |
| `user_account_id` | uuid | FK, part of unique key, indexed |
| `level` | text | `VIEW`, `EDIT`, `MANAGE` |
| `granted_by` | uuid | FK `user_account` |

Grants are the only source of access to a profile. See `06-security-and-ai-safety.md` section 3.

### `family_invitation`
| Column | Type | Notes |
|---|---|---|
| `id` | uuid | PK |
| `family_id` | uuid | FK, NOT NULL |
| `profile_id` | uuid | FK, NULL. When set, accepting also links this profile to the invited adult. |
| `invited_email` | text | NOT NULL, stored lowercase. Only this account can accept. |
| `role` | text | `MANAGER`, `MEMBER` |
| `token_hash` | text | NOT NULL, UNIQUE. SHA-256 of the token, which is never stored. |
| `invited_by` | uuid | FK `user_account` |
| `expires_at` | timestamptz | NOT NULL, 7 days after creation |
| `accepted_at`, `accepted_by` | | NULL until accepted. Single use. |

## 4. Health records

All tables in this section have `profile_id` and the provenance columns from section 1.2.

| Table | Main columns |
|---|---|
| `condition` | `name` NOT NULL, `status` (`ACTIVE`, `RESOLVED`, `UNKNOWN`), `onset_on`, `notes` |
| `allergy` | `allergen` NOT NULL, `reaction_as_recorded`, `severity_as_recorded`, `notes` |
| `dietary_restriction` | `kind` (`RESTRICTION`, `PREFERENCE`), `description` NOT NULL |
| `doctor_visit` | `occurred_on`, `occurred_time`, `doctor_name`, `facility`, `reason`, `notes` |
| `symptom_entry` | `description` NOT NULL, `severity_as_reported`, `occurred_on`, `occurred_time`, `notes` |

`emergency_contact` (`profile_id`, `name` NOT NULL, `relationship`, `phone` NOT NULL) has no provenance columns because it is not a health record.

`allergy`, `dietary_restriction`, and `emergency_contact` belong to Health Records (SRS 3.2). The meal assistant and emergency card depend on them.

## 5. Medication and lab results

### `medication`
`profile_id`, provenance columns, `name` NOT NULL, `dosage_as_recorded`, `instructions_as_recorded`, `prescribed_by`, `start_on`, `end_on`, `status` (`ACTIVE`, `STOPPED`, `UNKNOWN`).

Dosage is free text so the system preserves what was recorded and cannot be read as recommending a dose.

### `medication_schedule` (owned through `medication`)
`medication_id` FK, `time_of_day` NOT NULL, `days_of_week` (`smallint[]`, NULL means daily), `time_zone` NOT NULL, `reminder_enabled` NOT NULL.

### `adherence_event` (owned through `medication`)
`medication_id` FK, `schedule_id` nullable FK, `scheduled_for timestamptz`, `status` (`TAKEN`, `SKIPPED`), `recorded_at` NOT NULL, `recorded_by` FK.

Unique: (`schedule_id`, `scheduled_for`), so one dose cannot be logged twice. Removing a schedule sets `schedule_id` to NULL, which keeps the history it produced.

### `lab_result`
| Column | Type | Notes |
|---|---|---|
| `profile_id` | uuid | FK, NOT NULL |
| provenance columns | | Section 1.2 |
| `test_name` | text | NOT NULL, as recorded |
| `test_key` | text | NOT NULL, lowercase and trimmed `test_name`, used to group values for trends |
| `value_numeric` | numeric | NULL |
| `value_text` | text | NULL, for results like "Positive" |
| `unit` | text | NULL when missing from the source |
| `result_on` | date | NULL when missing from the source |
| `reference_range_as_printed` | text | NULL, display only |

`CHECK (value_numeric IS NOT NULL OR value_text IS NOT NULL)`.

`CLAUDE.md` asks for explicit units and dates, and SRS 3.6 requires missing ones to be flagged. This draft reconciles them as follows: both columns are explicit and nullable, the UI flags a missing value, and unit or date is never guessed.

Values are comparable in a trend only when `test_key` and `unit` match exactly, and `value_numeric` and `result_on` are present. No unit conversion takes place.

## 6. Documents and AI

### `document`
`profile_id`, `uploaded_by`, `original_filename`, `content_type` (`application/pdf`, `image/jpeg`, `image/png`, from the file's content), `size_bytes`, `sha256`, `storage_key` (UNIQUE, random, contains no personal data), `category` (`LAB_REPORT`, `PRESCRIPTION`, `SCAN`, `BILL`, `PHOTO`, `OTHER`), `document_on`, `description` (optional, user-entered), `extraction_status` (`NOT_REQUESTED`, `QUEUED`, `PROCESSING`, `READY_FOR_REVIEW`, `FAILED`, `REVIEWED`).

The stored file is never altered by the system. Each health record table's `source_document_id` is a foreign key to `document`.

### `record_attachment`
`profile_id`, `document_id` (FK, cascades), `record_type`, `record_id`, `created_by`. Unique: (`record_type`, `record_id`, `document_id`).

A document attached to a record as evidence, separate from provenance: a record has at most one source document but may have several attachments. `record_id` points into the table named by `record_type`, so it can't be a foreign key. The service checks that the record exists in the same profile, and deletes a record's attachments when it deletes the record (`DECISIONS.md`, D-020).

### `extraction_job` (owned through `document`)
`document_id`, `requested_by`, `status` (`QUEUED`, `PROCESSING`, `SUCCEEDED`, `FAILED`), `attempts`, `provider`, `model`, `prompt_version`, `error_code`, `started_at`, `finished_at`.

The job table doubles as a simple database-backed queue. The worker claims the oldest queued job with `FOR UPDATE SKIP LOCKED`, and calls the provider outside any transaction. No message broker is needed for the prototype.

### `extraction_draft` (owned through `document`)
`document_id`, `job_id`, `schema_version`, `status` (`PENDING_REVIEW`, `COMPLETED`, `REJECTED`), `reviewed_by`, `reviewed_at`. At most one draft per document is `PENDING_REVIEW` (partial unique index).

### `extraction_draft_item` (owned through `extraction_draft`)
`draft_id`, `position`, `item_type` (`MEDICATION`, `LAB_RESULT`, `CONDITION`, `ALLERGY`, `VISIT`), `payload jsonb`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `confirmed_record_id` (set exactly when approved), `decided_by`, `decided_at`.

`payload` holds the record type's own API request fields, so approval runs the same validation as manual entry. An approved record is `DOCUMENTED`, names the document as its source, has `extraction_draft_item_id` set (a foreign key that becomes NULL if the document and its drafts are deleted), and has the document attached.

Per-item status allows partial approval. Draft data stays out of the confirmed record tables until the user approves it, so a query on those tables can never return unapproved AI output.

### `ai_usage_event`
`user_account_id`, `feature`, `occurred_at`, `input_tokens`, `output_tokens`, `outcome`. Used for usage limits. Contains no prompt or response text.

## 7. Food, meals, summaries, emergency card, and audit

| Table | Main columns |
|---|---|
| `food_log_entry` | `profile_id`, `description` NOT NULL, `meal_type` (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`, `OTHER`, or NULL), `occurred_on`, `occurred_time`, `saved_recipe_id` nullable FK (added in Phase 14) |
| `saved_recipe` | `profile_id`, `created_by`, `title`, `ingredients jsonb` and `steps jsonb` (arrays of text), `mode` (`GENERAL`, `HEALTH_AWARE`), `prompt_version`. Added in Phase 14 (V11). `food_log_entry.saved_recipe_id` is `ON DELETE SET NULL`. |
| `health_summary` | `profile_id`, `generated_by`, `content jsonb` (points, gaps, conflicts, limitations, and a copy of each cited record), `period_from`, `period_to`, `source_refs jsonb` (type and ID of every record sent), `provider`, `model`, `prompt_version`. Added in Phase 16 (V12); never updated. |
| `emergency_card` | `profile_id` UNIQUE, `enabled`, `token_hash` UNIQUE, `token_rotated_at` |
| `emergency_card_item` | `card_id`, `item_type`, `display_text`, `display_order` |
| `audit_event` | `occurred_at`, `actor_account_id` nullable, `action`, `target_type`, `target_id`, `profile_id` nullable, `outcome`, `metadata jsonb` |

Notes:

- **`emergency_card_item` stores approved text, not references.** The card can only show what the user explicitly approved. An edit to a record cannot silently change or expand a public card. The UI flags when a source record has changed.
- **Only a hash of the emergency token is stored.** A database leak does not reveal working links.
- **`audit_event` is append-only and holds no health details.** Its actor and target references are deliberately not foreign keys, so the history outlives deleted records. `action` has no `CHECK` constraint, unlike other enumerations, because each later phase adds actions. The code's `AuditAction` lists them.
- **`spring_session` and `spring_session_attributes` belong to Spring Session JDBC** and follow its schema rather than the conventions above. Flyway creates them. The principal stored in a session is the account ID only.

## 8. Indexes and deletion

### Indexes
- `(profile_id, occurred_on DESC)` on every table that appears on the timeline.
- `(profile_id, test_key, unit, result_on)` on `lab_result` for trends.
- `(status, created_at)` on `extraction_job` for the worker.
- `(user_account_id)` on `profile_access_grant` to list a user's profiles.
- `(profile_id, occurred_at DESC)` and `(actor_account_id, occurred_at DESC)` on `audit_event`.

### Deletion
The source documents require preserving originals and also providing a way to delete demo records and files. This draft treats these as compatible:

- The system never deletes or alters an original on its own, including when extraction fails.
- An explicit user delete is a hard delete of the record, and for a document, of the stored file too.
- Deleting a document does not delete records already confirmed from it. The service clears `source_document_id` and sets `source_document_deleted_at` in the same transaction, and the UI shows "source document deleted".
- Deleting a profile removes all of its records and files, and writes an audit event.
- Demo reset is a separate, demo-only operation (see `08-deployment-guide.md`).

## 9. Migration plan

Migrations arrive with the phase that needs them, rather than as one schema up front.

| Phase | Migration content |
|---|---|
| 1 | `V1` baseline, no domain tables |
| 4 | `user_account`, session tables, `audit_event` |
| 5 | `family`, `family_membership`, `health_profile`, `profile_access_grant`, `family_invitation` |
| 6 | `condition`, `allergy`, `dietary_restriction`, `emergency_contact` |
| 7 | `doctor_visit`, `symptom_entry` |
| 8 | `medication`, `medication_schedule`, `adherence_event` |
| 9 | `lab_result` |
| 10 | `food_log_entry` |
| 11 | `document`, `record_attachment`, and the `source_document_id` foreign keys |
| 12 | `extraction_job`, `extraction_draft`, `extraction_draft_item`, `ai_usage_event` |
| 14 | `saved_recipe` |
| 16 | `health_summary` |
| 17 | `emergency_card`, `emergency_card_item` |

Provenance columns exist from each table's first migration. Their foreign keys are added once the target table exists: `source_document_id` in Phase 11 and `extraction_draft_item_id` in Phase 12.

## 10. Resolved review questions

- Blood group is not collected.
- Deletes are permanent, with no recycle bin in the prototype.
