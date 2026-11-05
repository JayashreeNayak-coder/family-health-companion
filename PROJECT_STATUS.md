# Family Health Companion
## Project Status

**Last updated:** 2026-11-05
**Current phase:** Phase 2 complete. Phase 3 is blocked on hosting decisions from the owner (see "Next step").
**Application code:** Technical foundation only. No health features or domain tables exist.

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
| 3 | Deploy the skeleton online with HTTPS | Blocked: needs hosting decisions |
| 4 | Identity, sessions, and audit log | Not started |
| 5 | Family, profiles, and authorization | Not started |
| 6 | Conditions, allergies, restrictions, emergency contacts, and provenance | Not started |
| 7 | Visits, symptoms, and first timeline | Not started |
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
| Backend integration tests (8) | Pass | Local (temporary PostgreSQL 18) and CI (Testcontainers) |
| Frontend tests (4), lint, type check, build | Pass | Local and CI |
| Docker image builds and full-stack check through nginx | Pass | CI only |

There are no backend unit tests yet, because there is no business logic.

## Known issues

- `npm audit` reports 10 moderate findings, all in development tools (ESLint's dependencies). Production dependencies report none. The only fix offered downgrades ESLint.
- Mockito prints a future-JDK warning about self-attaching during tests. Harmless today.
- Schema validation proves little until entities exist in Phase 4.

## Next step

The owner chooses one: give the hosting budget and domain for Phase 3, or approve moving Phase 3 to after Phase 5 and start Phase 4 now. Hosting costs money and needs the owner's account, so it can't proceed without them.
