# Family Health Companion
## Project Status

**Last updated:** 2026-11-05
**Current phase:** Phases 5 to 8 complete. Phase 3 (deployment) is waiting on the owner. Phase 9 is next.
**Application code:** Accounts, families, profiles and access grants, conditions, allergies, dietary items, emergency contacts, visits, symptoms, medications with schedules and adherence, and the timeline. No documents or AI.

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
| 3 | Deploy online with HTTPS (runs after Phase 5; needs budget, domain, and hosting account) | Waiting on the owner |
| 6 | Conditions, allergies, restrictions, emergency contacts, and provenance | Done |
| 7 | Visits, symptoms, and first timeline | Done |
| 8 | Medication, adherence, and reminders | Done |
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
| Backend unit tests (7) and integration tests (143) | Pass | Local (temporary PostgreSQL 18). CI: see the Git note below. |
| Frontend tests (42), lint, type check, build | Pass | Local. CI: see the Git note below. |
| Full flow from registration to linking and deleting a profile, through the Vite dev proxy against the real backend | Pass, with `curl` | Local |
| Docker image builds, and the auth flow through nginx | Last passed at Phase 4. Only CI can check this. | CI only |

**Git note:** Phases 5 to 8 are local commits until pushed, and CI only runs after a push.

## Known issues

- The pages haven't been viewed in a browser. Their behaviour is covered by frontend tests and the API flow by the checks above, but layout and styling are unchecked.
- `/auth/register` has no rate limit. Close this before going online in Phase 3 (`docs/06` section 2).
- Someone who knows an email can block that account's login for 15 minutes by failing repeatedly (`docs/06` section 2).
- If login fails straight after a successful registration, the user stays on the form, and resubmitting reports the email as taken. They can switch to sign in.
- `npm audit` reports 10 moderate findings, all in development tools (ESLint's dependencies). Production dependencies report none. The only fix offered downgrades ESLint.
- Mockito prints a future-JDK warning about self-attaching during tests. Harmless today.
- Invitations have no email delivery, so a manager passes the code on by hand, and they can't be revoked before they expire.
- Family members can't be removed and family roles can't be changed after joining.
- A recorded dose can't be corrected. Taken or skipped is final in this version.
- Reminders are the in-app "Doses due" list. Nothing is sent outside the app, and the list only updates when opened.
- Record lists in the UI show the first 100 items, and the timeline isn't paginated.
- Records can't be `DOCUMENTED` until documents exist in Phase 11.

## Next step

1. **Owner:** create the local databases (`README.md`), try the app in a browser, and report anything wrong.
2. **Owner:** review `DECISIONS.md`, D-019.
3. **Phase 9: lab results and trends.** Routine, so it can start without a further decision. Phases 10 and 11 follow.

Decisions the owner must make later:

- **Phase 3:** hosting budget, domain, and hosting account. Close the `/auth/register` rate limit first.
- **Phase 12:** the AI provider, an API key, and a usage budget (`docs/06` section 8.6).
