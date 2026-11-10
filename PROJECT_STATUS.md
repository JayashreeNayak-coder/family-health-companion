# Family Health Companion
## Project Status

**Last updated:** 2026-11-10
**Current phase:** Phases 5 to 12 and 14 to 16 complete and committed. Password reset, Google sign-in, and show-password added at the owner's request and committed. UI redesign UI-1 to UI-7 complete and committed. Phase 3 (deployment) is waiting on the owner.
**Application code:** Accounts (password or Google sign-in, with password reset by email), families, profiles and access grants, conditions, allergies, dietary items, emergency contacts, visits, symptoms, medications with schedules and adherence, lab results with trends, food log, document upload with attachments on records, AI reading of documents as a reviewed draft (Gemini), the timeline, and the meal assistant (general and for a family member) with saved recipes and adding a recipe to a food log, and cited record summaries.

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
| 9 | Lab results and trends | Done |
| 10 | Food log | Done |
| 11 | Document upload and private storage, with attachments on records | Done |
| 12 | AI layer, then extraction with review and approval | Done |
| 13 | Voice transcription | Deferred by the owner (`DECISIONS.md`, D-022) |
| UI-1 | Redesign: design tokens and core components | Done |
| UI-2 | Redesign: app shell, page addresses, sign-in | Done |
| UI-3 | Redesign: home overview and family sharing | Done |
| UI-4 | Redesign: profile header, overview, records | Done |
| UI-5 | Redesign: medications, lab results, timeline | Done |
| UI-6 | Redesign: documents and AI review | Done |
| UI-7 | Redesign: accessibility and responsive pass | Done |
| 14 | General meal assistant | Done |
| 15 | Health-aware meal assistant | Done |
| 16 | Record-based summaries | Done |
| Auth+ | Password reset by email, Google sign-in, show-password button (D-028) | Done |
| 17 | Emergency card | Not started |
| 18 | PDF export | Not started |
| 19 | Synthetic demo data, security review, and presentation prep | Not started |

## Tests

| Suite | Result | Where |
|---|---|---|
| Backend unit tests (46) and integration tests (223) | Pass | Local (temporary PostgreSQL 18). CI: see the Git note below. |
| Frontend tests (115), lint, type check, build | Pass | Local. CI: see the Git note below. |
| Full flow from registration to linking and deleting a profile, through the Vite dev proxy against the real backend | Pass, with `curl` (Phase 8) | Local |
| Lab trend, food log, upload, download, rejected HTML, 12 MB upload, attachment, and document deletion against the real backend | Pass, with `curl` | Local |
| Gemini client against a local stand-in server: request shape, key header, timeouts, and error codes | Pass | Local (unit) |
| A real Gemini extraction of a synthetic prescription PDF, approval of one item, and an injected instruction that was ignored | Pass (`gemini-3.5-flash`) | Local, owner's key |
| Screenshots of every page at 320, 390, 768, and 1280 px, with no sideways scrolling and no axe WCAG 2.2 A/AA violations (`npm run screenshots`) | Pass (after D-028), including the forgot and reset password pages, a summary and Meal ideas in both modes with a stubbed AI answer and a real save to the food log, the phone menu, the invite and record edit dialogs, the lab chart's hover readout, and a page refresh that keeps the open tab | Local, against the owner's running dev backend |
| Docker image builds, and the auth flow through nginx | Last passed at Phase 4. Only CI can check this. | CI only |

**Git note:** Phases 9 to 12 are local commits. The owner pushes; no CI run has been seen yet, and the owner is checking that Actions is enabled.

## Known issues

- The AI draft review screen has unit tests but no screenshot or axe check: a draft needs a real AI run. Look at it on the next real Gemini try.
- axe checks what a machine can. No screen-reader session (NVDA or VoiceOver) has been run by a person.
- A profile's Overview loads seven record lists separately (one request each). Fine for a prototype; a single summary endpoint could replace them later.
- Home has no "doses due today" summary. It would need one request per profile, or a new backend endpoint (owner's decision).
- An invitation can offer a dependent's profile to be linked to the invited account. The backend allows it and the old screen did the same. Owner to confirm whether only adult profiles should be offered.
- The sidebar's profile list doesn't see changes other family members make until the page is reloaded.
- `/auth/register` has no rate limit. Close this before going online in Phase 3 (`docs/06` section 2).
- Someone who knows an email can block that account's login for 15 minutes by failing repeatedly (`docs/06` section 2).
- Google sign-in with the owner's keys was checked on 2026-11-10 up to Google's own sign-in page, which accepted the client ID and return address. The owner hasn't yet finished a full sign-in. The owner's first try showed no button because the backend ran in a terminal opened before `setx`. The backend now logs at startup whether Google sign-in and email are on (README troubleshooting).
- Reset emails have not been sent through a real Gmail account yet. On 2026-11-10 the owner's `MAIL_USERNAME` wasn't a full email address and `MAIL_PASSWORD` wasn't a 16-letter app password; the owner must correct both.
- Password reset requests are limited per account (3 an hour), not per address.
- On phones, a profile's section tabs scroll sideways and the last visible label is cut off.
- If login fails straight after a successful registration, the user stays on the form, and resubmitting reports the email as taken. They can switch to sign in.
- `npm audit` reports 10 moderate findings, all in development tools (ESLint's dependencies). Production dependencies report none. The only fix offered downgrades ESLint.
- Mockito prints a future-JDK warning about self-attaching during tests. Harmless today.
- Invitations have no email delivery, so a manager passes the code on by hand, and they can't be revoked before they expire.
- Family members can't be removed and family roles can't be changed after joining.
- A recorded dose can't be corrected. Taken or skipped is final in this version.
- Reminders are the in-app "Doses due" list. Nothing is sent outside the app, and the list only updates when opened.
- Record lists in the UI show the first 100 items, and the timeline isn't paginated.
- Uploaded files are on the server's disk until object storage is chosen in Phase 3 (`DECISIONS.md`, D-020). Back that folder up with the database.
- No virus scanning of uploads (`docs/06` section 5).
- A document's category, date, and description can't be edited after upload.
- An upload attached from a record, where the attach step then fails, stays in Documents unattached.
- Lab trends are shown for one test and unit at a time. Units are never converted.
- Docker image changes for document storage are verified only by CI.
- On Gemini's free tier, Google may use uploaded documents to improve its products and people may read them. Synthetic documents only (`DECISIONS.md`, D-021).
- AI reading was tried once on a real model with one synthetic PDF. `gemini-flash-latest` was returning 503 (high demand), so the default is `gemini-3.5-flash` with `gemini-flash-lite-latest` as a fallback. Free-tier availability varies.
- The AI never fills a condition's status, a medication's status, or a visit's time. The user adds them after approval.
- An approved draft item can't be undone from the draft. Edit or delete the record instead.
- The meal assistant (both modes) and summaries haven't been tried against real Gemini yet.
- A summary keeps a copy of the records it cites. If a record is later edited or deleted, the summary doesn't show that.
- Summaries can't be exported yet (Phase 18). Its tests use a fake provider and a local stand-in server.
- Saved recipes have API endpoints but no screen of their own yet. A food log entry made from a recipe shows only the recipe's title.
- Ingredients to avoid are checked by plain text matching. It misses other names and foods made from them, so it only flags (`docs/06` section 8.5).

## Next step

1. **Owner:** confirm CI runs on GitHub (pushed on 2026-11-06).
2. **Owner:** start the backend from PowerShell as in `README.md`, and try **Read with AI** on a synthetic prescription or lab report.
3. **Owner:** review `DECISIONS.md`, D-019 and D-020. D-021 (free tier, synthetic data only) is confirmed.
4. **UI/UX redesign:** done and committed (`9a0d52a`). **Owner:** review `DECISIONS.md`, D-024.
5. **Phases 14 to 16** are committed (`41bf1e0`).
6. **Password reset, Google sign-in, show-password** (D-028) are committed. **Owner:** fix `MAIL_USERNAME`/`MAIL_PASSWORD`, fully restart VS Code, restart the backend, and try both (README troubleshooting). Phase 17 (emergency card) is next; Phase 18 (PDF export) needs a PDF library, which needs the owner's approval.

Decisions the owner must make later:

- **Phase 3:** hosting budget, domain, and hosting account. Close the `/auth/register` rate limit first.
- **Before real health data:** a paid AI tier or another provider (`DECISIONS.md`, D-021).
