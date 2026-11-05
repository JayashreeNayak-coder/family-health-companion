# Family Health Companion
## Project Status

**Last updated:** 2026-11-05
**Current phase:** Phase 4 complete. Phase 5 plan awaiting approval. Phase 3 moved to after Phase 5 (D-016).
**Application code:** Accounts, sessions, and audit log. No health features or family tables exist.

## Environment

| Item | State |
|---|---|
| Project at `C:\dev\family-health-companion` | Done |
| Git repository and local identity | Done |
| Private GitHub repository | Connected. Account and private visibility verified before pushing. |
| Local databases in the Windows PostgreSQL service | **Not created.** The owner runs the commands in `README.md`, because they need the PostgreSQL admin password. |
| Local Docker / WSL2 | Not set up, by decision (`DECISIONS.md`, D-011) |
| Old copy in OneDrive | Still present. Owner agreed in principle; remove it from a new session started in `C:\dev\family-health-companion`, after confirming it holds nothing newer. |
| Empty repository at `C:\Users\deban\.git` | Removed after confirming it was empty |

## Documents

| Document | State |
|---|---|
| `docs/01` | Approved source document |
| `docs/02` (v0.3), `docs/03` (v0.2) | Approved. Phase 0 corrections applied (`DECISIONS.md`, D-014). |
| `docs/04` to `docs/08` | Approved v1.0 baseline, content delegated by the owner (`DECISIONS.md`, D-015) |

## Roadmap

| Phase | Scope | State |
|---|---|---|
| 0 | Repository and implementation planning | Done |
| 1 | Repository foundation and minimal end-to-end app | Done |
| 2 | Design baseline: finalize `docs/04` to `docs/08`, fix inconsistencies | Done |
| 4 | Identity, sessions, and audit log | Done |
| 5 | Family, profiles, and authorization | Done |
| 3 | Deploy online with HTTPS (runs after Phase 5; needs budget, domain, and hosting account) | Not started |
| 6 | Conditions, allergies, restrictions, emergency contacts, and provenance | Done |
| 7 | Visits, symptoms, and first timeline | Done |
| 8 | Medication, adherence, and reminders | Not started |
| 9 | Lab results and trends | Not started |
| 10 | Food log | Not started |
| 11 | Document upload and private storage | Not started |
| 12 | AI layer, then extraction with review and approval | Not started |
| 13 | Voice transcription | Not started |
| 14 | General meal assistant | Not started |
| 15 | Health-aware meal assistant | Not started |
| 16 | Record-based summaries | Not started |
| 17 | Emergency card | Not started |
| 18 | PDF export | Not started |
| 19 | Synthetic demo data, security review, and presentation prep | Not started |

## Tests

| Suite | Result | Where |
|---|---|---|
| Backend unit tests (7) and integration tests (45) | Pass | Local (temporary PostgreSQL 18) and CI (Testcontainers) |
| Frontend tests (12), lint, type check, build | Pass | Local and CI |
| Auth flow through the Vite dev proxy against the real backend | Pass, by hand with `curl` | Local |
| Docker image builds, and the auth flow through nginx | Pass | CI only |

## Known issues

- The pages haven't been viewed in a browser. Their behaviour is covered by frontend tests and the API flow by the checks above, but layout and styling are unchecked.
- `/auth/register` has no rate limit. Close this before going online in Phase 3 (`docs/06` section 2).
- Someone who knows an email can block that account's login for 15 minutes by failing repeatedly (`docs/06` section 2).
- If login fails straight after a successful registration, the user stays on the form, and resubmitting reports the email as taken. They can switch to sign in.
- `npm audit` reports 10 moderate findings, all in development tools (ESLint's dependencies). Production dependencies report none. The only fix offered downgrades ESLint.
- Mockito prints a future-JDK warning about self-attaching during tests. Harmless today.

## Next step

Approve or change this Phase 5 plan. Do not start until the owner approves.

**Phase 5: family, profiles, and authorization** (see `docs/04` section 3, `docs/05` section 4, and `docs/06` section 3)

- **New dependencies:** none expected.
- **Migration:** `V3` adds `family`, `family_membership`, `health_profile`, and `profile_access_grant`.
- **Backend:** packages `com.familyhealth.family` and `.profile`, including the single access check, `profileAccess.require(account, profileId, level)`. Endpoints from `docs/05` section 4: families, invitations, profiles, and grants.
- **Frontend:** create a family, add and list profiles, and manage access.
- **Rules:** grants are the only source of access; no access returns 404 and too low a level returns 403; a manager has no automatic access to another adult's records; at least one account always holds `MANAGE`.

**Question to settle before starting:** `docs/04` has no table for family invitations, although `docs/05` has invitation endpoints. Phase 5 would add `family_invitation`, storing only a hash of the token, with an expiry.

**Acceptance tests:** the authorization matrix from `docs/07` section 3 on every profile endpoint, the rules above, audit events for grant changes and profile deletion, and frontend states. Then run all checks locally and in CI.

**On approval:** implement, commit, verify the GitHub account and private status, push, confirm CI, report, and stop.
