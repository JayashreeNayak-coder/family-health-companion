# Family Health Companion
## Architectural and Project Decisions

Only decisions the project owner has approved are recorded here. Draft proposals stay in the `docs/` documents until they are reviewed.

| ID | Date | Decision | Reason |
|---|---|---|---|
| D-001 | 2026-11-05 | Project stack as given in the project brief: React + TypeScript, Java + Spring Boot, PostgreSQL, Spring Data JPA, Flyway, REST, modular monolith, Docker-based online prototype | Approved direction |
| D-002 | 2026-11-05 | Project lives at `C:\dev\family-health-companion`, in its own Git repository with `main` as the default branch | The previous location was in a OneDrive-synced folder and inside a Git repository rooted at the home folder |
| D-003 | 2026-11-05 | Commits in this repository use the identity `jayashree Nayak <jayashreefour20@gmail.com>`, set in the repository's local Git config | Project owner's instruction. Global Git config is unchanged. |
| D-004 | 2026-11-05 | Private GitHub repository for CI: `https://github.com/JayashreeNayak-coder/family-health-companion.git` | Approved. URL provided by the owner. Access and private visibility are not yet verified. |
| D-005 | 2026-11-05 | Maven (with Wrapper) as the backend build tool | Not specified in the system design. Approved. |
| D-006 | 2026-11-05 | Base Java package `com.familyhealth` | Approved |
| D-007 | 2026-11-05 | Frontend and API are served from the same origin | Consistent with `03-system-design.md` section 6, and removes the need for CORS |
| D-008 | 2026-11-05 | Development Docker: Docker Engine inside WSL2 if the machine supports it, with local PostgreSQL as the fallback | Docker Desktop's published requirements don't list Windows 10 Home. WSL setup requires separate confirmation from the owner. |
| D-009 | 2026-11-05 | Roadmap order in `PROJECT_STATUS.md` replaces the sequence in `03-system-design.md` section 9 | Medication and lab records must exist before AI extraction can produce them, and deployment and tests must not wait until the end. `03-system-design.md` section 9 now matches. |
| D-010 | 2026-11-05 | Commits in this repository carry no Claude co-author line | Project owner's instruction |
| D-011 | 2026-11-05 | For now, local development uses the Windows PostgreSQL service, and Docker and Testcontainers run in CI. Replaces the WSL2 preference in D-008, which can be revisited later. | WSL2 is compatible but needs administrator rights and a restart, memory is tight (7.8 GB), and it may affect the Android emulator. CI still covers the Docker checks. |
| D-012 | 2026-11-05 | TypeScript is pinned to 6.0.x, and frontend dependencies use exact versions | `typescript-eslint` doesn't support TypeScript 6.1 or later yet. Exact versions keep builds repeatable. |
| D-013 | 2026-11-05 | Family managers have no automatic access to another adult's records. When a manager-created profile is linked to an adult's account, the manager's access drops to what the adult chooses to keep. | Owner confirmed. Favours adult privacy over a manager's convenience. |
| D-014 | 2026-11-05 | Requirement and design corrections from Phase 0 applied: SRS v0.3 and system design v0.2 | Allergies and contacts gain an owner, symptoms have one owner, a minimal audit log is in scope, "verified" is dropped, missing lab data is flagged, voice is in scope, and deletion rules are stated. |
| D-015 | 2026-11-05 | `docs/04` to `docs/08` are the approved v1.0 baseline. Recommended answers to the review questions were applied. | The owner delegated final content rather than reviewing line by line. Each later phase should raise anything it finds wrong. |
| D-016 | 2026-11-05 | Online deployment (Phase 3) moves to after Phase 5. Phase numbers stay the same. | Owner approved. Avoids hosting cost for now, gives the first demo login and profiles to show, and CI already runs the full Docker stack. |
