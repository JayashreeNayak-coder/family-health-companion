# Family Health Companion
## AI Assistant Context

Read `CLAUDE.md` first, then `PROJECT_STATUS.md`. This file holds context that those files and the code don't make obvious.

## Resuming in a new conversation
This repository is the project's memory, so a new conversation doesn't need the earlier chat. To resume, the owner can say: "Continue from PROJECT_STATUS.md."

1. Work from `C:\dev\family-health-companion`. Any copy in OneDrive is old and out of date.
2. Read `CLAUDE.md`, `PROJECT_STATUS.md`, and this file, then continue from "Next step" in `PROJECT_STATUS.md`.
3. Before ending any phase, update `PROJECT_STATUS.md` and this file, so the next conversation can continue from them.

## Saving tokens (owner's request)
- Read only the documents and sections the current phase needs, not all of `docs/`. Search before reading whole files.
- Don't spawn subagents unless asked. Keep replies short.
- Don't repeat earlier plans or paste long logs. Filter command output to what matters.
- Keep plans brief, but still stop for approval between phases.

## Current state
- Identity, sessions, and the audit log exist (Phase 4), with no health features yet. See `PROJECT_STATUS.md` for the phase and what is blocked.
- `docs/01` to `docs/08` are the approved baseline.

## Git
- The repository root must be `C:\dev\family-health-companion`. Check with `git rev-parse --show-toplevel`.
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
- To run integration tests without the owner's database password, start a throwaway PostgreSQL with the installed binaries (`initdb` and `pg_ctl`) on a spare port and a scratch data folder, then set `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`. Don't pipe `pg_ctl start` in PowerShell: the server inherits the pipe and the command never returns.
- `gh` isn't installed. The GitHub API is reachable with `curl`, using the token from `git credential fill`.

## Backend tests
- Tests named `*IntegrationTest` run under Failsafe (`verify`) and need a database. Surefire (`test`) excludes them.
- Spring Boot 4 has moved test annotations to new packages, for example `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Integration tests extend `IntegrationTestBase` and use `ApiClient`, which fetches a real CSRF token and keeps the session cookie. Don't bypass security in tests.
- The local test database keeps its data, so use `ApiClient.uniqueEmail()`. Each `ApiClient` also gets its own address, so failed logins in one test can't rate limit another.
- Session cookie attributes and forwarded addresses only work on an embedded server. Test them in `RealServerIntegrationTest`, not with MockMvc.
- Don't add a `Filter` as a Spring bean inside the security chain, or Boot registers it a second time as a servlet filter. `SecurityConfig` creates `SessionPolicyFilter` with `new`.

## Working agreement
- Work in approved phases, and stop for approval between them.
- Roadmap order is in `PROJECT_STATUS.md`. It replaces `docs/03-system-design.md` section 9 (see `DECISIONS.md`, D-009).
- Don't report a test as passing unless it was actually run.
