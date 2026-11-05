# Family Health Companion
## API Specification — Draft v0.1

**Status:** DRAFT for review. Not approved and not an implementation requirement until reviewed.
**Implementation state:** Nothing in this document has been implemented.
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

## 3. Authentication [Proposal]

Phase 4 proposal: a server-side session in a cookie, with CSRF protection.

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create an account |
| POST | `/auth/login` | Start a session, rotating the session ID |
| POST | `/auth/logout` | End the session |
| GET | `/auth/me` | Current user |
| GET | `/auth/csrf` | Issue the CSRF token for the SPA |

Login failures return the same response whether or not the email exists.

## 4. Family and profiles

| Method | Path | Minimum access |
|---|---|---|
| POST | `/families` | Signed in |
| GET | `/families/{familyId}` | Family member |
| POST | `/families/{familyId}/invitations` | Family manager |
| POST | `/families/invitations/{token}/accept` | Signed in, invited user |
| GET | `/profiles` | Returns only profiles the user has a grant for |
| POST | `/families/{familyId}/profiles` | Family manager |
| GET | `/profiles/{profileId}` | `VIEW` |
| PUT | `/profiles/{profileId}` | `EDIT` |
| DELETE | `/profiles/{profileId}` | `MANAGE` |
| GET | `/profiles/{profileId}/grants` | `MANAGE` |
| PUT | `/profiles/{profileId}/grants/{accountId}` | `MANAGE` |
| DELETE | `/profiles/{profileId}/grants/{accountId}` | `MANAGE` |

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
| PUT | `/profiles/{id}/medications/{medId}/schedules` | Set schedule | `EDIT` |
| POST | `/profiles/{id}/medications/{medId}/adherence` | Record taken or skipped | `EDIT` |
| GET | `/profiles/{id}/medications/due?date=` | Doses due, the in-app reminder list | `VIEW` |
| GET | `/profiles/{id}/lab-results/trends?testKey=&unit=` | Comparable values only, plus a count of excluded values | `VIEW` |

Trend responses contain values and dates only, with no interpretation such as "normal" or "abnormal".

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

## 10. Questions for review

- **Q1.** Should adding a family member's account work through invitation links (as drafted), or is that more than the prototype needs?
- **Q2.** Should someone with `VIEW` be able to generate AI summaries? This draft says yes, since summaries reveal nothing beyond what `VIEW` already shows, though it does use AI quota.
