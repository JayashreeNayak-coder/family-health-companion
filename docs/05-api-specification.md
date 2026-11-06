# Family Health Companion
## API Specification — v1.0

**Status:** Approved baseline. Written from the problem statement, SRS, and system design; the owner delegated final content (`DECISIONS.md`, D-015). Change it through the normal approval process.  
**Implementation state:** See `PROJECT_STATUS.md`. A described item may not be built yet.  
**Based on:** `02-srs.md`, `03-system-design.md`, `04-database-design.md` (draft), `CLAUDE.md`.

This document defines conventions and the endpoint inventory. Once implemented, the generated OpenAPI document becomes the detailed field-level reference, and this file stays as the summary.

## 1. Conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1` |
| Format | JSON, except file upload and download, and PDF export |
| Origin | The frontend and API share one origin (approved), so no CORS configuration is needed |
| Authentication | Session cookie, see section 3 |
| IDs | UUID strings |
| Dates | ISO 8601: `2026-11-05` for dates, `08:30` for local times, `2026-11-05T08:30:00Z` for system timestamps |
| Unknown values | `null`, never a placeholder such as `0` or `"unknown"` |
| Request bodies | Dedicated request DTOs. JPA entities are never exposed. |
| Validation | Jakarta Bean Validation on the backend |
| Concurrency | Records return `version`. An update with a stale `version` returns 409. |

## 2. Responses

### Pagination
List endpoints accept `page` and `size` (default 20, maximum 100) and return:

```json
{ "items": [], "page": 0, "size": 20, "totalItems": 0 }
```

### Errors
All errors use RFC 9457 Problem Details, with a stable `code` for the frontend:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "errors": [ { "field": "testName", "message": "must not be blank" } ]
}
```

Error messages never contain health details, stack traces, or SQL.

| Status | Use |
|---|---|
| 400 | Validation failure or malformed request |
| 401 | Not signed in |
| 403 | Signed in and can see the resource, but lacks the required level (for example `VIEW` trying to edit), or a CSRF failure |
| 404 | Resource does not exist **or** the user has no access to its profile |
| 409 | Stale version or conflicting state |
| 413 / 415 | Upload too large or file type not allowed |
| 429 | Rate or AI usage limit reached |
| 503 | AI provider unavailable. Manual workflows are unaffected. |

Returning 404 for profiles a user cannot access means the API does not reveal that a given profile exists.

## 3. Authentication

Phase 4 uses a server-side session in a cookie, with CSRF protection.

| Method | Path | Access | Request | Success |
|---|---|---|---|---|
| POST | `/auth/register` | Public | `email`, `password`, `displayName` | 201, account. Does not sign in. |
| POST | `/auth/login` | Public | `email`, `password` | 200, account. Rotates the session ID and CSRF token. |
| POST | `/auth/logout` | Signed in | None | 204 |
| GET | `/auth/me` | Signed in | None | 200, account |
| GET | `/auth/csrf` | Public | None | 200, `headerName` and `token` |

An account response is `id`, `email`, and `displayName`.

Every POST, including login and register, must send the token from `/auth/csrf` in the named header, or it returns 403 with code `CSRF_REJECTED`. The token changes at login, so the SPA fetches a new one before each write.

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid input, with `errors` listing each field |
| 401 | `INVALID_CREDENTIALS` | Login failed. Identical for a wrong password, an unknown email, and a disabled account. |
| 401 | `UNAUTHENTICATED` | Not signed in |
| 409 | `EMAIL_ALREADY_REGISTERED` | Registration with an email already in use |
| 429 | `RATE_LIMITED` | Too many failed logins, with a `Retry-After` header |

The 409 does reveal that an email is registered. Hiding it would need email verification, which is deferred (`06-security-and-ai-safety.md` section 2).

## 4. Family and profiles

| Method | Path | Minimum access |
|---|---|---|
| POST | `/families` | Signed in |
| GET | `/families` | Returns only the caller's families |
| GET | `/families/{familyId}` | Family member |
| POST | `/families/{familyId}/invitations` | Family manager. Offering a profile also needs `MANAGE` on it. |
| GET | `/families/invitations/{token}` | Signed in, invited user. Shows the family, and the profile on offer. |
| POST | `/families/invitations/{token}/accept` | Signed in, invited user |
| GET | `/profiles` | Returns only profiles the user has a grant for |
| POST | `/families/{familyId}/profiles` | Family manager for `kind` `DEPENDENT` or `ADULT`. Any member for `SELF`. |
| GET | `/profiles/{profileId}` | `VIEW` |
| PUT | `/profiles/{profileId}` | `EDIT` |
| DELETE | `/profiles/{profileId}` | `MANAGE` |
| GET | `/profiles/{profileId}/grants` | `MANAGE` |
| PUT | `/profiles/{profileId}/grants/{accountId}` | `MANAGE` |
| DELETE | `/profiles/{profileId}/grants/{accountId}` | `MANAGE` |

Invitation rules:

- The token is returned once, when the invitation is created. There is no email delivery in the prototype, so the manager passes the code on.
- An unknown, expired, used, or someone else's invitation returns the same 404 (`INVITATION_NOT_FOUND`).
- When the invitation offers a profile, `accept` needs `keepAccess` (`NONE`, `VIEW`, or `EDIT`), the adult's choice of what others keep. Without it the response is 400 (`KEEP_ACCESS_REQUIRED`).

Grant rules:

- Grants can only go to family members, otherwise 404 (`MEMBER_NOT_FOUND`).
- Removing or lowering the last `MANAGE` returns 409 (`LAST_MANAGER`).
- Removing or lowering the linked adult's own `MANAGE` returns 409 (`LINKED_ACCOUNT_GRANT`).

## 5. Profile-owned records

These collections share one pattern under `/profiles/{profileId}`:

| Resource | Path |
|---|---|
| Conditions | `/conditions` |
| Allergies | `/allergies` |
| Dietary restrictions | `/dietary-restrictions` |
| Emergency contacts | `/emergency-contacts` |
| Doctor visits | `/visits` |
| Symptoms | `/symptoms` |
| Medications | `/medications` |
| Lab results | `/lab-results` |
| Food log | `/food-log` |

| Method | Path | Minimum access |
|---|---|---|
| GET | `/{resource}` | `VIEW` |
| POST | `/{resource}` | `EDIT` |
| GET | `/{resource}/{id}` | `VIEW` |
| PUT | `/{resource}/{id}` | `EDIT` |
| DELETE | `/{resource}/{id}` | `EDIT` |

The service must confirm that `{id}` belongs to `{profileId}`. If it doesn't, it returns 404.

Record responses include `sourceType` and `sourceDocumentId`, so the UI can always show where a value came from.

### Additional record endpoints

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/profiles/{id}/timeline?from=&to=&types=` | Chronological records, with a "date unknown" group | `VIEW` |
| GET | `/profiles/{id}/medications/{medId}/schedules` | Read schedules | `VIEW` |
| PUT | `/profiles/{id}/medications/{medId}/schedules` | Replace schedules. One that keeps the same doses keeps its recorded history. | `EDIT` |
| POST | `/profiles/{id}/medications/{medId}/adherence` | Record taken or skipped. 400 `NOT_A_SCHEDULED_DOSE`, 400 `DOSE_IN_FUTURE`, 409 `ALREADY_RECORDED`. | `EDIT` |
| GET | `/profiles/{id}/medications/due?date=` | Doses of active medications due on a date, the in-app reminder list | `VIEW` |
| GET | `/profiles/{id}/lab-results/trend-series` | Each test and unit with comparable values, to choose a trend from | `VIEW` |
| GET | `/profiles/{id}/lab-results/trends?testKey=&unit=` | Comparable values only, oldest first, plus a count of excluded values. Leaving out `unit` means values with no unit. | `VIEW` |

Trend responses contain values and dates only, with no interpretation such as "normal" or "abnormal".

The timeline returns `entries`, newest first, and `dateUnknown`, the undated records. A date range doesn't hide undated records, because they can't be placed inside or outside it. `types` still applies to them. The timeline isn't paginated yet. It currently covers conditions (by onset date), visits, symptoms, medications (by start date), and lab results (by result date). The food log isn't on the timeline.

A `sourceDocumentId` must name a document in the same profile, or the request returns 404 (`SOURCE_DOCUMENT_NOT_FOUND`). A document implies `DOCUMENTED` when `sourceType` is left out. `DOCUMENTED` without a document returns 400 (`SOURCE_DOCUMENT_REQUIRED`), and `USER_REPORTED` with one returns 400 (`SOURCE_TYPE_MISMATCH`). Provenance can't be changed by an update.

## 6. Documents and extraction

| Method | Path | Purpose | Access |
|---|---|---|---|
| POST | `/profiles/{id}/documents` | Multipart upload | `EDIT` |
| GET | `/profiles/{id}/documents` | List metadata | `VIEW` |
| GET | `/profiles/{id}/documents/{docId}/content` | Stream the original through the backend | `VIEW` |
| DELETE | `/profiles/{id}/documents/{docId}` | Explicit delete | `MANAGE` |
| POST | `/profiles/{id}/documents/{docId}/extraction` | Queue extraction (202) | `EDIT` |
| GET | `/profiles/{id}/documents/{docId}/draft` | Read draft for review | `EDIT` |
| PATCH | `/profiles/{id}/documents/{docId}/draft/items/{itemId}` | Correct an item | `EDIT` |
| POST | `/profiles/{id}/documents/{docId}/draft/items/{itemId}/approve` | Create a confirmed record | `EDIT` |
| POST | `/profiles/{id}/documents/{docId}/draft/items/{itemId}/reject` | Reject an item | `EDIT` |

Upload fields are `file`, `category` (`PRESCRIPTION`, `LAB_REPORT`, `SCAN`, `BILL`, `PHOTO`, `OTHER`, default `OTHER`), `documentOn`, and `description`. The type comes from the file's content: anything other than PDF, JPEG, or PNG returns 415 (`UNSUPPORTED_FILE_TYPE`), an empty file 400 (`FILE_REQUIRED`), and one over 10 MB 413 (`FILE_TOO_LARGE`). `GET /profiles/{id}/documents/{docId}` returns metadata only. Responses never include the storage key or hash. The extraction and draft endpoints arrive in Phase 12.

### Attachments

A document can be attached to records as evidence, such as a clinic bill on a visit or a prescription photo on a medication. Attaching or detaching changes neither the record nor the document.

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/profiles/{id}/attachments?recordType=&recordId=` | One type's attachments, or one record's, each with its document's metadata | `VIEW` |
| POST | `/profiles/{id}/attachments` | `{recordType, recordId, documentId}`. 404 `RECORD_NOT_FOUND` or `DOCUMENT_NOT_FOUND` unless both are in the profile, 409 `ALREADY_ATTACHED`. | `EDIT` |
| DELETE | `/profiles/{id}/attachments/{attachmentId}` | Detach. The document stays. | `EDIT` |

`recordType` is `CONDITION`, `ALLERGY`, `DIETARY_RESTRICTION`, `VISIT`, `SYMPTOM`, `MEDICATION`, `LAB_RESULT`, or `FOOD_LOG`. Deleting a record removes its attachments, and deleting a document removes the attachments to it.

Document content is never available through a public or storage URL. Responses use `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, and `Cache-Control: no-store`.

## 7. AI features

| Method | Path | Purpose | Access |
|---|---|---|---|
| POST | `/ai/transcriptions` | Voice note to text, returned for user editing | Signed in |
| POST | `/meal-assistant/general` | General recipes; uses no profile data | Signed in |
| POST | `/profiles/{id}/meal-assistant/health-aware` | Uses that profile's relevant data | `VIEW` |
| POST | `/profiles/{id}/recipes` | Save a recipe | `EDIT` |
| POST | `/profiles/{id}/food-log/from-recipe/{recipeId}` | Add to food log | `EDIT` |
| POST | `/profiles/{id}/summaries` | Generate a record-based summary | `VIEW` |
| GET | `/profiles/{id}/summaries/{summaryId}` | Read a summary | `VIEW` |
| GET | `/profiles/{id}/summaries/{summaryId}/pdf` | Export as PDF | `VIEW` |

Every AI response includes these fields:

```json
{
  "contentType": "AI_GENERATED",
  "limitations": ["No dietary restrictions are recorded for this profile."],
  "disclaimer": "This is a suggestion, not medical advice."
}
```

Switching meal modes is a frontend concern: ingredients stay in client state, so the user doesn't re-enter them.

## 8. Emergency card

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/profiles/{id}/emergency-card` | Read card settings and items | `MANAGE` |
| PUT | `/profiles/{id}/emergency-card` | Replace the approved items | `MANAGE` |
| POST | `/profiles/{id}/emergency-card/enable` | Enable; returns the link once | `MANAGE` |
| POST | `/profiles/{id}/emergency-card/disable` | Disable immediately | `MANAGE` |
| POST | `/profiles/{id}/emergency-card/rotate` | New link; the old link stops working | `MANAGE` |
| GET | `/public/emergency-cards/{token}` | **Public.** Approved items only. | Valid token |

The public endpoint is rate limited and returns `Cache-Control: no-store` and `X-Robots-Tag: noindex`. An unknown token and a disabled card return the same 404. The response has no profile ID, family information, or record IDs.

## 9. System

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/actuator/health` | Up or down only, with no details | Public |

No other actuator endpoint is exposed.

## 10. Resolved review questions

- Family accounts are added through invitation links.
- `VIEW` access can generate AI summaries, subject to usage limits.
