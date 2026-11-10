# Family Health Companion

A web application that helps individuals and families organize health records. It is a record-management and support tool. It does not diagnose, prescribe, or replace healthcare professionals.

**Status:** early prototype. Accounts, families, profiles with access control, health history, visits, symptoms, medications with in-app reminders, lab results with trends, a food log, private document upload with photos and files attached to records, and the timeline work. AI reading of uploaded documents works with a Gemini key, as a draft you review and approve item by item. Other AI features don't exist yet. See [PROJECT_STATUS.md](PROJECT_STATUS.md).

**Use synthetic data only.** Do not enter real health information.

## Stack

React and TypeScript (Vite), Java 25 and Spring Boot, PostgreSQL 18, Spring Data JPA, Flyway, Docker.

## Run locally

These commands are for Windows PowerShell. You need JDK 25, Node 22 (22.13 or later), and PostgreSQL 18. Docker isn't required for local development.

### 1. Create the local databases

Run `psql -h localhost -U postgres`, then run the following, replacing both passwords with ones you choose:

```sql
CREATE ROLE family_health_dev LOGIN PASSWORD 'choose-a-dev-password';
CREATE DATABASE family_health_dev OWNER family_health_dev;
CREATE ROLE family_health_test LOGIN PASSWORD 'choose-a-test-password';
CREATE DATABASE family_health_test OWNER family_health_test;
```

### 2. Start the backend

```powershell
cd backend
$env:DB_PASSWORD = 'your-dev-password'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The backend runs at `http://localhost:8081`, and Flyway applies migrations at startup. Uploaded files go to `backend\data\documents`, which Git ignores. Set `DOCUMENT_STORAGE_DIR` to use another folder.

### Optional: AI reading of documents

AI reading uses Google Gemini. Without a key, the app works normally and the **Read with AI** button reports that AI isn't available.

1. Create a key in Google AI Studio.
2. In PowerShell, run `setx GEMINI_API_KEY "your-key"`, then open a **new** terminal before starting the backend. Never put the key in a file in this repository or paste it into a chat.

On Gemini's free tier, Google may use what you send to improve its products, and people may read it. Upload synthetic documents only.

### Optional: password reset emails (Gmail)

Without a mail account, the backend started with the `dev` profile writes each reset email, with its link, to the backend console instead. To send real emails:

1. Turn on 2-Step Verification for the Gmail account, then create an **app password** at https://myaccount.google.com/apppasswords.
2. In PowerShell, run `setx MAIL_USERNAME "your.address@gmail.com"` and `setx MAIL_PASSWORD "the-16-letter-app-password"`, then open a **new** terminal before starting the backend.

### Optional: sign in with Google

The **Continue with Google** button appears only when both keys are set.

1. In Google Cloud Console (https://console.cloud.google.com), create a project, then open **APIs & Services → OAuth consent screen**. Choose **External**, fill in the app name and your email, and add yourself (and any testers) under **Test users**.
2. Open **Credentials → Create credentials → OAuth client ID**, type **Web application**.
3. Under **Authorized redirect URIs**, add `http://localhost:5173/login/oauth2/code/google`. Add the real address's `/login/oauth2/code/google` once the app is online.
4. In PowerShell, run `setx GOOGLE_CLIENT_ID "..."` and `setx GOOGLE_CLIENT_SECRET "..."`, then open a **new** terminal before starting the backend.

Sign in through `http://localhost:5173`, the frontend's address, so Google's return address matches. Never put these values in a file in this repository or paste them into a chat.

### If the Google button or reset emails don't appear

`setx` only reaches programs started **after** it runs. A terminal inside VS Code inherits VS Code's environment, so close VS Code completely and reopen it (or use a new PowerShell window outside it), then restart the backend. At startup the backend log says which features are on:

- `Google sign-in is on`, or `Google sign-in is off: ...`.
- `Email sending is on`, or `Email sending is off: ...` (the reset link is then printed in the backend log).
- `MAIL_USERNAME is not an email address ...`: set it to the full Gmail address.
- After a reset request, `Email could not be sent: MailAuthenticationException ...` means Gmail refused the address or password. `MAIL_PASSWORD` must be the 16-letter app password, not the account's normal password.

### 3. Start the frontend

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. You should see the sign-in page, where you can create an account.

To try sharing, create a second account in a private browser window. The first account creates a family and an invitation for the second account's email, and the second pastes the code into **Join a family**. There is no email delivery yet.

The `dev` profile turns off the `Secure` cookie attribute, because local development uses plain HTTP. Every other environment keeps it on unless `SESSION_COOKIE_SECURE` is set to `false`.

## Tests

### Backend

```powershell
cd backend
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/family_health_test'
$env:TEST_DB_USERNAME = 'family_health_test'
$env:TEST_DB_PASSWORD = 'your-test-password'
.\mvnw.cmd verify
```

Without `TEST_DB_URL`, integration tests start PostgreSQL through Testcontainers, which requires Docker. CI works this way.

`.\mvnw.cmd test` runs unit tests only and needs no database.

### Frontend

```powershell
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

Screenshot checks need the backend and `npm run dev` running, plus a one-time `npx playwright install chromium`. They create a synthetic account, capture every page at phone, tablet, and desktop widths into `frontend/e2e/screenshots/`, and fail if a page scrolls sideways:

```powershell
npm run screenshots
```

## Run the full stack with Docker

This requires Docker, which isn't set up on the current development machine, so CI runs this check.

```sh
cp .env.example .env    # then set DB_PASSWORD in .env
docker compose up --build
```

Open `http://localhost:8080`. This stack is for local HTTP only, so `.env.example` sets `SESSION_COOKIE_SECURE=false`. nginx serves the frontend and forwards `/api/` and `/actuator/health` to the backend. Other actuator paths aren't forwarded.

## Documentation

| Document | Purpose |
|---|---|
| [Problem statement](docs/01-problem-statement.md) | Why this exists |
| [SRS](docs/02-srs.md) | Requirements |
| [System design](docs/03-system-design.md) | Architecture |
| [Database design](docs/04-database-design.md) | Tables and ownership |
| [API specification](docs/05-api-specification.md) | Endpoints and conventions |
| [Security and AI safety](docs/06-security-and-ai-safety.md) | Access rules and safety |
| [Testing strategy](docs/07-testing-strategy.md) | What gets tested |
| [Deployment guide](docs/08-deployment-guide.md) | Environments and hosting |
| [Decisions](DECISIONS.md) | Approved decisions |
