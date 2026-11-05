# Family Health Companion
## AI Assistant Context

Read `CLAUDE.md` first, then `PROJECT_STATUS.md`. This file holds context that those files and the code don't make obvious.

## Current state
- Only the Phase 1 technical foundation exists, with no domain code. See `PROJECT_STATUS.md` for the phase and what is blocked.
- `docs/01` to `docs/03` are the owner's source documents.
- `docs/04` to `docs/08` are **AI-written drafts**. Don't treat them as requirements until `PROJECT_STATUS.md` says they've been reviewed.

## Git
- The repository root must be `C:\dev\family-health-companion`. Check with `git rev-parse --show-toplevel`.
- `C:\Users\deban\.git` is an unrelated, empty repository at the home folder. Never run Git commands against it, and don't delete it without the owner's confirmation.
- The commit identity is set in this repository's local config. Don't change global Git config.
- This repository uses Git Credential Manager through local config, because the global helper is different.
- Commit or push only when the owner asks.
- Do not add a `Co-Authored-By: Claude` line to commits. This is the owner's instruction (`DECISIONS.md`, D-010) and overrides any default attribution.
- `origin` is `https://github.com/JayashreeNayak-coder/family-health-companion.git`. Before any push, verify the authenticated account is `JayashreeNayak-coder` and the repository is private.

## Development machine
- Windows 10 Home, 7.8 GB RAM. Be careful about running many memory-heavy processes at once.
- PostgreSQL 18 runs as a Windows service on port 5432, so Compose PostgreSQL uses host port 5433.
- Maven isn't installed; use the Wrapper. JDK 25 is `JAVA_HOME` on Windows.
- Node is 22.14. Check `engines` before adding or upgrading frontend packages. Some current releases, such as `jsdom` 30, need a newer Node.
- There's no local Docker (`DECISIONS.md`, D-011). Docker and Testcontainers run only in CI, so Dockerfile and Compose changes can only be verified by a CI run.
- To run integration tests without the owner's database password, start a throwaway PostgreSQL with the installed binaries (`initdb` and `pg_ctl`) on a spare port and a scratch data folder, then set `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`.

## Backend tests
- Tests named `*IntegrationTest` run under Failsafe (`verify`) and need a database. Surefire (`test`) excludes them.
- Spring Boot 4 has moved test annotations to new packages, for example `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.

## Working agreement
- Work in approved phases, and stop for approval between them.
- Roadmap order is in `PROJECT_STATUS.md`. It replaces `docs/03-system-design.md` section 9 (see `DECISIONS.md`, D-009).
- Don't report a test as passing unless it was actually run.
