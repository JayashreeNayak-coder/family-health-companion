# Family Health Companion
## Testing Strategy — v1.0

**Status:** Approved baseline. Written from the problem statement, SRS, and system design; the owner delegated final content (`DECISIONS.md`, D-015). Change it through the normal approval process.  
**Implementation state:** See `PROJECT_STATUS.md`. A described item may not be built yet.  
**Based on:** `02-srs.md` section 9, `03-system-design.md` section 2, `CLAUDE.md` section 9.

## 1. Principles

- Tests ship in the same phase as the code they cover.
- Don't mock away the behaviour under test. In particular, **authorization and persistence are tested against real PostgreSQL**.
- AI providers are replaced at the provider interface only, so everything on our side of it still runs for real.
- Every phase report lists tests that were run, with results, and tests that were not run.
- Tests use synthetic data only.

## 2. Test levels

| Level | Tools | Covers | Runs |
|---|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Business rules and validation | Every build |
| Integration | Spring Boot Test, Testcontainers (PostgreSQL 18), MockMvc | Migrations, repositories, security, full API flows | Every build |
| Frontend | Vitest, React Testing Library | Components, states, labels, and errors | Every build |
| End-to-end | Playwright, a dependency to approve later | A few critical journeys against the full stack | Added in Phase 19 |
| AI safety | Fake provider, plus optional real provider runs | Safety behaviour | Fake on every build; real on demand |

Backend test classes follow the Maven convention: `*Test` runs under Surefire and `*IntegrationTest` under Failsafe.

## 3. Required coverage by area

### Authorization (highest priority)
A reusable test matrix applies to **every** profile-owned endpoint:

| Caller | Expected result |
|---|---|
| Not signed in | 401 |
| Signed in, no grant | 404, and no data changed |
| `VIEW` on a read | 200 |
| `VIEW` on a write | 403, and no data changed |
| `EDIT` on a write | Success |
| Valid grant on profile A, with a record ID from profile B | 404 |
| Same family, no grant | 404 |

Adding an endpoint without this matrix fails review.

### Other backend areas

| Area | Must verify |
|---|---|
| Migrations | All migrations apply to an empty database, and Hibernate validation passes |
| Validation | Each DTO rejects invalid input with the standard error format |
| Provenance | `DOCUMENTED` without a source is rejected by the service and the database |
| Lab trends | Different units are not combined; missing values or dates are excluded and counted |
| Adherence | One dose can't be logged twice |
| Timeline | Correct order, all record types, and undated records in their own group |
| Uploads | Wrong type, renamed file, oversize, and empty files are rejected; a stored file is returned byte for byte |
| Documents | Files can't be reached without authorization, and originals survive failed extraction |
| Draft approval | No confirmed record exists before approval; approval links to the source; rejection creates nothing |
| Emergency card | Disabled or rotated links return 404; only approved items are returned; response contains no internal IDs |
| Audit | Sensitive actions write events, and events hold no health content |
| Logging | A test run with known synthetic values confirms they don't appear in logs |

### AI failure handling
With a fake provider, verify each of: timeout, provider error, malformed JSON, schema violation, empty result, and usage limit reached. In all cases manual workflows must keep working, and nothing must be saved as confirmed.

### AI safety
A versioned set of synthetic cases covers:

- **Summary:** cited records must exist; missing data is disclosed; a request for a diagnosis is refused.
- **Extraction:** a document with a missing unit yields `null`, not a guess; a document containing injected instructions doesn't change behaviour.
- **Meals:** general mode sends no profile data, asserted on the captured provider request; health-aware mode sends only allowed fields and discloses gaps.

The fake provider proves that our validation and data handling work, but it cannot show that a real model behaves safely. So before each demo and each change of prompt or model, real provider runs must be done and their results recorded. These are not part of normal CI because of cost and non-determinism.

### Frontend
- Loading, empty, error, and success states for each screen.
- Source labels are displayed, and AI content is always labelled.
- Review screens don't offer a save without approval.
- The emergency card warning appears before enabling.
- Missing values are shown as missing, not as blank or zero.

## 4. SRS acceptance traceability

| SRS section 9 criterion | Verified by | Phase |
|---|---|---|
| Users manage authorized profiles securely | Authorization matrix, profile integration tests | 5 |
| Records created manually and viewed chronologically | Record and timeline integration tests | 6–10 |
| Documents uploaded and retrieved without loss | Byte-for-byte test | 11 |
| AI extraction requires approval | Draft approval tests | 12 |
| Unauthorized users can't access records | Authorization matrix | 5 onward |
| General and health-aware modes work as specified | Provider request assertions, real provider runs | 14–15 |
| Summaries stay grounded and avoid diagnosis | Grounding tests, safety cases | 16 |
| Emergency card is opt-in and limited | Emergency card tests | 17 |
| PDF exports are labelled | PDF content test | 18 |
| Core workflows tested; setup documented | CI and following the guide on a clean environment | 19 |

## 5. Running tests

These commands were confirmed in Phase 1.

| Purpose | Command | Requires |
|---|---|---|
| Backend unit tests | `./mvnw test` | JDK 25 |
| Backend, all tests | `./mvnw verify` | JDK 25, and `TEST_DB_URL` or Docker |
| Frontend tests | `npm test` | Node 22.13 or later |
| Frontend lint and types | `npm run lint`, `npm run typecheck` | Node 22.13 or later |

### Database for integration tests
Integration tests always run against real PostgreSQL 18, and a test fails if the server is another major version.

- **In CI:** Testcontainers starts `postgres:18-alpine`.
- **On the development machine:** Docker isn't set up (`DECISIONS.md`, D-011), so `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` point at a dedicated local test database. The README has the setup commands.

The local database keeps its data between runs, unlike a fresh container. Each later phase's tests must clean up after themselves, or use unique data, so they pass in both setups.

Docker image builds and the full-stack check run only in CI for now.

## 6. CI gates

GitHub Actions runs on each push and pull request. All of the following must pass:

1. Backend: `./mvnw verify`, including integration tests.
2. Frontend: lint, type check, tests, production build.
3. Docker image builds for backend and frontend, then a full-stack check: the page and health endpoint respond through nginx.

Coverage is reported, but no percentage target is enforced. The authorization matrix and the areas above are the real requirements, and a percentage can be met without testing them.

## 7. Resolved review questions

- Playwright end-to-end tests are added in Phase 19.
- Real AI safety runs are manual, before each demo and each prompt or model change.
