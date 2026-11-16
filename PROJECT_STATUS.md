# Family Health Companion
## Project Status

**Last updated:** 2026-11-11
**Current phase:** Phase 3a (hosting code) done for free hosting on Cloudflare Pages, Render, Neon, and R2 (`DECISIONS.md`, D-033). Phase 3b (creating the accounts and deploying) is the owner's, following `docs/08` section 9. Phase 19 (synthetic demo data, security review, presentation prep) comes after.
**Application code:** Accounts (password or Google sign-in, with password reset by email), families, profiles and access grants, conditions, allergies, dietary items, emergency contacts, visits, symptoms, medications with schedules and adherence, lab results with trends, food log, document upload with attachments on records, AI reading of documents as a reviewed draft (Gemini), the timeline, and the meal assistant (general and for a family member) with saved recipes and adding a recipe to a food log, cited record summaries with PDF export, an emergency card shared by link or QR code, renaming and deleting families, and photos for profiles and families.

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
| 3a | Hosting code: R2 storage, Brevo email, Pages proxy and proxy secret, registration limit, `render.yaml` (D-033) | Done |
| 3b | Create the Neon, R2, Brevo, Render, and Pages accounts and deploy (`docs/08` section 9) | Waiting on the owner |
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
| 17 | Emergency card | Done |
| 18 | PDF export | Done |
| Fam+ | Rename and delete a family, unique names per account, profile and family photos (D-031) | Done |
| 19 | Synthetic demo data, security review, and presentation prep | Not started |

## Tests

| Suite | Result | Where |
|---|---|---|
| Backend unit tests (69) and integration tests (258) | Pass (2026-11-11, Phase 3a) | Local (temporary PostgreSQL 18). CI: see the Git note below. |
| Frontend tests (137, including the Pages proxy), lint, type check, build | Pass (2026-11-11) | Local. CI: see the Git note below. |
| Full flow from registration to linking and deleting a profile, through the Vite dev proxy against the real backend | Pass, with `curl` (Phase 8) | Local |
| Lab trend, food log, upload, download, rejected HTML, 12 MB upload, attachment, and document deletion against the real backend | Pass, with `curl` | Local |
| Gemini client against a local stand-in server: request shape, key header, timeouts, and error codes | Pass | Local (unit) |
| A real Gemini extraction of a synthetic prescription PDF, approval of one item, and an injected instruction that was ignored | Pass (`gemini-3.5-flash`) | Local, owner's key |
| Screenshots of every page at 320, 390, 768, and 1280 px, with no sideways scrolling and no axe WCAG 2.2 A/AA violations (`npm run screenshots`) | Pass (after D-028), including the forgot and reset password pages, a summary and Meal ideas in both modes with a stubbed AI answer and a real save to the food log, the phone menu, the invite and record edit dialogs, the lab chart's hover readout, and a page refresh that keeps the open tab, the emergency card (choose lines, save, turn on, then the public page from the link), the Manage family dialog with a family photo upload, and a profile photo shown on the profile and Home | Local, against the owner's running dev backend |
| Docker image builds, and the auth flow through nginx | Last passed at Phase 4. Only CI can check this. | CI only |

**Git note:** The owner pushes. The CI run for commit `120d355` failed (2026-11-11). Reproduced locally: `npm run typecheck` rejected `e2e/screenshots.spec.ts`, because its local `Buffer.from` type took one argument and the new avatar line passed `'base64'`. Fixed. Lint, type check, frontend tests, the frontend build, and backend `verify` pass locally. The Docker and full-stack CI job, including the changed Dockerfile, can only be confirmed by the next CI run.

## Known issues

- The AI draft review screen has unit tests but no screenshot or axe check: a draft needs a real AI run. Look at it on the next real Gemini try.
- axe checks what a machine can. No screen-reader session (NVDA or VoiceOver) has been run by a person.
- A profile's Overview loads seven record lists separately (one request each). Fine for a prototype; a single summary endpoint could replace them later.
- Home has no "doses due today" summary. It would need one request per profile, or a new backend endpoint (owner's decision).
- An invitation can offer a dependent's profile to be linked to the invited account. The backend allows it and the old screen did the same. Owner to confirm whether only adult profiles should be offered.
- The sidebar's profile list doesn't see changes other family members make until the page is reloaded.
- The hosted demo hasn't been deployed yet. R2 storage and Brevo email are tested only against local stand-in servers; the real services are checked by the smoke test in `docs/08` section 9.
- On Render's free plan, the first request after 15 quiet minutes can take a minute or more. The app shows no "waking up" message yet.
- Brevo replaces a Gmail sender address, so reset emails from the demo may land in spam.
- Cloudflare Pages deploys the frontend on every push, without waiting for CI.
- Someone who knows an email can block that account's login for 15 minutes by failing repeatedly (`docs/06` section 2).
- Google sign-in with the owner's keys was checked on 2026-11-10 up to Google's own sign-in page, which accepted the client ID and return address. The owner hasn't yet finished a full sign-in. The owner's first try showed no button because the backend ran in a terminal opened before `setx`. The backend now logs at startup whether Google sign-in and email are on (README troubleshooting).
- Gmail accepted the owner's app password on 2026-11-10 (checked by signing in without sending). The owner has not yet confirmed a reset email arriving in an inbox.
- Password reset requests are limited per account (3 an hour), not per address.
- A profile's section tabs scroll sideways and a label is cut off at the edge: on phones, and since the Emergency card tab (Phase 17) also at 1280 px for managers.
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
- Docker image changes for document storage are verified only by CI. On 2026-11-10 the backend `Dockerfile` was found to have literal `
` text instead of line continuations in its `RUN` line since Phase 11, which would most likely have failed the image build; fixed, but not yet built. Image processing and PDFBox need `java.desktop`, which the Temurin JRE image includes; also unverified until CI runs.
- On Gemini's free tier, Google may use uploaded documents to improve its products and people may read them. Synthetic documents only (`DECISIONS.md`, D-021).
- AI reading was tried once on a real model with one synthetic PDF. `gemini-flash-latest` was returning 503 (high demand), so the default is `gemini-3.5-flash` with `gemini-flash-lite-latest` as a fallback. Free-tier availability varies.
- The AI never fills a condition's status, a medication's status, or a visit's time. The user adds them after approval.
- An approved draft item can't be undone from the draft. Edit or delete the record instead.
- The meal assistant (both modes) and summaries haven't been tried against real Gemini yet.
- A summary keeps a copy of the records it cites. If a record is later edited or deleted, the summary doesn't show that.
- Summaries can't be exported yet (Phase 18). Its tests use a fake provider and a local stand-in server.
- Saved recipes have API endpoints but no screen of their own yet. A food log entry made from a recipe shows only the recipe's title.
- The emergency card's QR code is shown only when a link is made, because only then is the token known. To print it again, make a new link.
- Public emergency card reads are rate limited in memory (30 a minute per IP address): lost on restart, not shared between instances.
- The screenshot runs and the reset check on 2026-11-10 left synthetic accounts in the local dev database (`screens-...@example.test`, `reset-check-...@example.com`).
- PDFs use Noto Sans through PDFBox, which does no text shaping: Devanagari and other scripts that need it can't be shown, and missing characters print as "?". A shaping-capable renderer or per-script fonts would be needed for Indian-language names.
- The family name check is in the service, so two simultaneous requests from one account could both create a family with the same name.
- Deleting a family needs its manager to be the only member, and members can't be removed yet, so a family with other members can't be deleted at all for now.
- Photos are cached by the viewer's browser for up to an hour; someone whose access is removed may still see a cached photo for that long.
- Ingredients to avoid are checked by plain text matching. It misses other names and foods made from them, so it only flags (`docs/06` section 8.5).

## Next step

1. **Owner:** confirm CI runs on GitHub (pushed on 2026-11-06).
2. **Owner:** start the backend from PowerShell as in `README.md`, and try **Read with AI** on a synthetic prescription or lab report.
3. **Owner:** review `DECISIONS.md`, D-019 and D-020. D-021 (free tier, synthetic data only) is confirmed.
4. **UI/UX redesign:** done and committed (`9a0d52a`). **Owner:** review `DECISIONS.md`, D-024.
5. **Phases 14 to 16** are committed (`41bf1e0`).
6. **Password reset, Google sign-in, show-password** (D-028) are committed. **Owner:** create a Gmail app password, `setx MAIL_PASSWORD` with it, fully restart VS Code, restart the backend, and check its log says `Mail server sign-in works` (README troubleshooting).
7. **Phase 17 (emergency card)** is done (D-029). **Owner:** restart the backend (migration V14) and try it from a profile you manage.
8. **Phase 18 (PDF export)** is done with PDFBox (D-032), along with family management and photos (D-031). **Owner:** restart the backend (migration V15) and try them; send a password reset to confirm the email arrives.
9. **Phase 3a** is done (D-033). **Owner:** commit and push, check that CI passes, then follow `docs/08` section 9 to create the accounts and deploy, and run its smoke test.
10. **Phase 19** (synthetic demo data, security review, presentation prep) is next.

Decisions the owner must make later:

- **Later, optional:** a domain of your own (better email delivery through Brevo), or Render's paid plan (no sleeping, SMTP allowed).
- **Before real health data:** a paid AI tier or another provider (`DECISIONS.md`, D-021).
