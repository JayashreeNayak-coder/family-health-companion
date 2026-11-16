# Family Health Companion
## Security and AI Safety — v1.0

**Status:** Approved baseline. Written from the problem statement, SRS, and system design; the owner delegated final content (`DECISIONS.md`, D-015). Change it through the normal approval process.  
**Implementation state:** See `PROJECT_STATUS.md`. A described item may not be built yet.  
**Based on:** `02-srs.md` sections 4 and 5, `03-system-design.md` section 7, `CLAUDE.md` sections 6 to 8.

**This prototype is not production-certified and has had no formal security review.** It must hold synthetic data only in public demonstrations.

## 1. Data classification

| Class | Examples | Handling |
|---|---|---|
| Health data | Records, documents, summaries, drafts | Authorized access only. Never logged. |
| Credentials and secrets | Password hashes, session IDs, API keys, card tokens | Never logged or returned. Secrets come from the environment. |
| Account data | Email, display name | Authorized access only. Avoid in logs. |
| Public by user choice | Approved emergency card items | Visible to anyone with the link |

## 2. Authentication

| Control | Decision | Reason |
|---|---|---|
| Mechanism | Server-side session with a cookie | Can be revoked immediately, and tokens stay out of browser storage |
| Cookie | `HttpOnly`, `Secure`, `SameSite=Lax` | Limits theft through scripts and cross-site use |
| CSRF | Spring Security CSRF, with the token sent as a header by the SPA | Required when using cookie authentication |
| Passwords | BCrypt through Spring's `DelegatingPasswordEncoder` | Needs no extra dependency, and the algorithm can be upgraded later |
| Password policy | 12 characters minimum and 72 bytes maximum, no composition rules | Length matters most, and BCrypt can only use 72 bytes |
| Brute force | After failed logins, 429 for 15 minutes: 10 failures per account or 50 per IP. Generic failure message. | Reduces password guessing and account discovery. The IP limit is higher because a classroom or household shares one address. |
| Session | ID and CSRF token rotated at login; expires after 30 minutes idle and 12 hours in total | Reduces fixation and leftover sessions |
| Stored sessions | Spring Session JDBC | Sessions survive restarts and can be revoked |
| Disabled accounts | Checked on every request, which ends any open session | Disabling takes effect immediately |
| Client address | `X-Forwarded-For` is trusted only from private network addresses (nginx) | Clients can't fake an address to escape the IP limit |
| Password reset | Emailed single-use link (Gmail SMTP, D-028), valid 30 minutes, at most 3 an hour per account. Only the token's hash is stored, and the token sits after `#` in the link so it never reaches a server log or Referer. The request answers the same for any email, and the email is sent in the background so timing doesn't tell either. Using a link ends every session of the account. | Recovery without an administrator, without revealing which emails have accounts |
| Google sign-in | OpenID Connect through Spring Security, on only when both Google keys are set. The app keeps its own session holding only the account ID. A new Google-verified email creates an account; a matching verified email links to the existing account; an unverified email is refused. An account already linked to another Google ID is never taken over. | Lets families sign in with Google while keeping one account per email (D-028) |

**Known limitations:**
- Rate limit counters are held in memory, so a restart clears them and they aren't shared between instances. This suits the single-instance prototype.
- Someone who knows an email can block that account's login for 15 minutes by failing repeatedly.
- `/auth/register` is limited to 20 valid attempts an hour per IP address (Phase 3, D-033). It still reveals whether an email is registered, at that rate.

- Password reset requests are limited per account, not per address, so someone can make the app send up to 3 emails an hour to any registered address.
- With Gmail SMTP, emails come from a personal Gmail address and are limited by Gmail's daily sending cap. A real sending domain belongs with Phase 3.

Deferred, as `03-system-design.md` section 7 allows: multi-factor login and email verification for password accounts. Self-service password recovery was added after Phase 16 (D-028).

## 3. Authorization model

The SRS requires "family-member-specific access permissions" but does not define them. This model defines them. Rules 3 and 4 are recorded in `DECISIONS.md`, D-013.

### Levels
| Level | Can do |
|---|---|
| `VIEW` | Read the profile's records and documents; use its data for summaries and health-aware meals |
| `EDIT` | `VIEW`, plus add, change, and delete records, upload documents, and approve drafts |
| `MANAGE` | `EDIT`, plus manage grants, the emergency card, document deletion, and profile deletion |

### Rules
1. **Grants are the only source of access.** Belonging to a family does not grant access to anyone's records.
2. **Dependents:** the family manager who creates a dependent profile receives `MANAGE`.
3. **Adults with an account:** the adult receives `MANAGE` on their own profile. When a manager-created profile is linked to an adult's account, the manager's access drops to whatever the adult chooses to keep. By default the adult is asked at linking.
4. **Family managers have no automatic access to another adult's records.**
5. **A dependent turning 18 changes nothing automatically.** Linking an account is a deliberate step. Date of birth is optional and often missing, so an automatic rule would be unreliable.
6. **At least one account must always hold `MANAGE`** on a profile.

### Enforcement
- Every profile operation calls a single check in the service layer, for example `profileAccess.require(account, profileId, Level.EDIT)`.
- Repository queries for child records always filter on `profile_id`, so an ID from another profile cannot return data.
- The frontend may hide controls, but the backend alone decides.
- No access returns 404; insufficient level on a visible profile returns 403.

## 4. Protecting data

| Area | Control |
|---|---|
| In transit | HTTPS only in demo and production, with HSTS |
| At rest | Provider encrypted disks for the database and server-side encryption in object storage |
| Backups | Encrypted, off-server, restore tested |
| Secrets | Environment variables on the server, GitHub Actions secrets in CI, `.env` ignored by Git |
| Logs | IDs and event types only. No record content, file names, prompts, AI responses, or tokens. |
| Headers | `Content-Security-Policy`, `X-Content-Type-Options`, `Referrer-Policy: no-referrer`, frame blocking |

**Known limitation:** this is not field-level encryption. Someone with database access can read health data. This meets the prototype's SRS section 5 requirement in a basic way, and should be revisited before commercial use.

## 5. Document uploads

- **Allowed types:** PDF, JPEG, PNG. HTML, SVG, and Office files are rejected.
- **Type check:** the file's actual content signature is checked, never only the extension or the browser's claimed type.
- **Size limit:** 10 MB per file, enforced by both nginx and Spring.
- **Storage:** private bucket, with random keys containing no personal data. Until hosting is chosen, a private folder on the server that nothing else serves (`DECISIONS.md`, D-020).
- **Scans and X-rays** are stored as evidence only. The system never interprets an image.
- **Download:** streamed through the backend after an access check, sent as an attachment.
- **Not included in the prototype:** virus scanning. Listed as a limitation.

### Profile and family photos

- JPEG or PNG only, by content signature; at most 5 MB and 40 megapixels, checked before the image is decoded.
- Re-encoded on the server as a 512 px JPEG, which keeps only the pixels: EXIF data such as GPS location and camera details is dropped.
- Stored like documents, under random keys, and served only through the backend: a profile photo to anyone with `VIEW`, a family photo to its members. Only `EDIT` (profile) or a family manager (family) may change one.

## 6. Emergency card

- Off by default. The user selects each item to show.
- The enable screen states that anyone with the link or QR code may be able to view the card.
- The token is 256 bits of secure randomness. Only its hash is stored, and the full link is shown only when created.
- The user can disable the card or rotate the link at any time, and both take effect immediately.
- The card has no route to the rest of the profile, the documents, or the family.
- The endpoint is rate limited (30 reads a minute per IP address, found or not) and blocks caching and indexing. The page also sets a `robots` `noindex` tag.
- The token is in the link's `#` part, so opening the page doesn't send it to a server log. Malformed, unknown, disabled, and replaced links all return the same 404.
- Turning the card off forgets the token hash, so turning it on again always makes a new link.
- Changes (save, on, off, new link) are audited without their content. Views aren't, because anyone with a link could fill the table.

## 7. Audit trail

Audit events are written for: login success and failure, logout, account changes, grant changes, profile deletion, document upload, download and deletion, draft approval and rejection, emergency card changes, PDF export, and denied access attempts.

Events record who, what, when, and the outcome, and never the health content involved. The SRS requires this, so a minimal version is in scope for the prototype. Advanced audit reporting stays deferred.

To keep the table from being filled without signing in, three things aren't recorded: signed-out requests answered with 401, CSRF failures from signed-out callers, and login attempts already blocked by the rate limit. A failed login stores the account ID only when the email exists, and never the submitted email.

## 8. AI safety rules

### 8.1 What AI must never do
- Diagnose, predict disease or outcomes, or declare someone healthy.
- Prescribe, stop, or change medication, or suggest doses.
- Claim that a food caused a symptom, or that a recipe is medically safe.
- Prescribe a therapeutic diet.
- Invent missing values, units, dates, or records.

### 8.2 Controls for each feature

| Feature | Data sent to the provider | Safety behaviour |
|---|---|---|
| Document extraction | The document only | Draft stays separate until approved per item. Fields not found are `null`. If unreliable, the document is kept and no structured data is created. |
| Voice transcription (deferred, D-022) | Audio only; not kept after transcription | Text is returned for the user to edit |
| Summary | Allergies, undated records, and conditions, visits, symptoms, medications, and lab results in the chosen period: type, date, title, the value or dosage line, and source type, under `R1`-style references. No notes, documents, names, or database IDs. | Must cite source records; the backend rejects any answer citing a record it didn't send. Reports gaps and conflicts. No interpretation. Takes no free text, so it can't be asked for a diagnosis. The user confirms each time. |
| General meals | Ingredients and request text only | Uses no profile data, even if a member is selected in the UI. The endpoint takes no profile ID. Recipes naming an ingredient the user listed to avoid are flagged, never called safe. |
| Health-aware meals | Allergen names, restriction and preference text for the selected profile; condition names only for the conditions the user ticks. No reactions, notes, dates, names, or documents. | Discloses missing data. Never claims safety. The user sees what will be sent and confirms each time; the response lists what was used. |

Names, dates of birth, and contact details are removed from AI requests unless the feature requires them.

### 8.3 Grounding and labelling
- Summaries are checked after generation: each cited record must exist in the data sent, otherwise the response is rejected.
- All AI content is visibly labelled in the UI and in PDF exports.
- The UI distinguishes **User reported**, **From document**, and **AI generated**.
- PDFs state that they are user-generated record summaries, not official medical certificates, at the top and in every page footer, and label the content AI-generated (Phase 18). They are made on the server with Apache PDFBox and the bundled Noto Sans font (OFL); characters the font lacks print as "?", and scripts that need shaping, such as Devanagari, aren't supported.
- Where relevant, responses encourage talking to a qualified healthcare professional.

### 8.4 Treating documents as untrusted
Text in an uploaded document could try to instruct the AI (prompt injection). So:
- Document content is passed as data, separately from instructions.
- Models receive no tools and cannot read other records.
- Output must match a strict schema, and any other output is rejected.
- Nothing becomes a record without human review.

### 8.5 Allergy check limitation
The system design calls for a backend check of recipes against known allergies. Text matching catches obvious conflicts, but it misses synonyms, derived ingredients, and translations.

The check can therefore only ever flag a conflict. **A recipe with no flag must not be described as safe**, and every health-aware result states that ingredients should be checked by the user.

### 8.6 Provider integration
- Providers are called only from the backend, behind interfaces such as `DocumentExtractor`, `Transcriber`, `SummaryGenerator`, and `RecipeGenerator`.
- Prompts and output schemas are versioned files in the backend, and the version is stored with each result.
- All calls have timeouts and bounded retries. Malformed responses are rejected, not repaired by guessing.
- Usage limits apply per account and feature, and a global daily limit protects the budget.
- Provider unavailability returns 503, while all manual workflows keep working.
- **Provider selection criteria (Phase 12):** no training on submitted data, a clear retention policy, structured output, and document and audio support at an acceptable cost.
- **Chosen provider:** Google Gemini, called over HTTP with the key in a header, from `ai.AiConfig` only. The owner plans to use the free tier, which does **not** meet the first criterion: Google may use free-tier inputs and outputs to improve its products, and human reviewers may read them. The free tier is therefore acceptable only with synthetic demo documents, and the UI says so. Real health data needs the paid tier or another provider first (`DECISIONS.md`, D-021).
- **Limits:** for each AI feature separately (document extraction, general meals, health-aware meals, summaries), 20 requests per account and 200 overall in any 24 hours by default (`AI_LIMIT_PER_ACCOUNT_PER_DAY`, `AI_LIMIT_GLOBAL_PER_DAY`). A job is tried at most twice, and only after a timeout or provider outage.

## 9. Demo environment

- Synthetic data only, with a visible banner stating this and warning against entering real health information.
- A low usage limit on AI.
- A demo-only reset that cannot run in production configuration.

## 10. Resolved review questions

- Virus scanning stays out of the prototype, as a documented limitation to fix before commercial use.
- Email verification and password recovery are deferred until before real users. An administrator resets demo passwords.
- The emergency card has the full SRS behaviour: opt-in, approved items only, disable, and rotate.
