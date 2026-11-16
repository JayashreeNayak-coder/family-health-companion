# Family Health Companion
## Deployment Guide — v1.0

**Status:** Approved baseline. Written from the problem statement, SRS, and system design; the owner delegated final content (`DECISIONS.md`, D-015). Change it through the normal approval process.  
**Implementation state:** See `PROJECT_STATUS.md`. A described item may not be built yet.  
**Based on:** `03-system-design.md` sections 6 to 8, `CLAUDE.md`.

## 1. Environments

| Environment | Purpose | Data | Configuration |
|---|---|---|---|
| Development | Local work | Synthetic | `.env` (not in Git) |
| Test | CI and integration tests | Temporary, per run | Testcontainers |
| Demo | Teacher review and client demos | Synthetic only | Server environment variables |
| Production | Future, after a security review | Not in scope | Not defined yet |

## 2. Development machine setup

**Machine checked on 2026-11-05:** Windows 10 Home, build 19045, 7.8 GB RAM, hardware virtualization enabled, WSL features disabled, PostgreSQL 18 service already using port 5432.

**Current setup (`DECISIONS.md`, D-011):** local development uses the Windows PostgreSQL service, following `README.md`. Docker image builds, Testcontainers, and the full-stack check run in GitHub Actions.

**The rest of this section is optional**, for adding Docker locally later. Docker Desktop's published requirements don't list Windows 10 Home and require 8 GB of RAM, so these steps use Docker Engine inside WSL2. Because a Windows JVM can't reach that Docker safely, integration tests would then run inside WSL.

**Side effect to expect:** enabling WSL2 activates Windows' virtualization platform. VirtualBox and the Android emulator can run more slowly or need configuration changes afterwards.

### Step 1: enable WSL2 (requires administrator rights and a restart)
In PowerShell, opened with **Run as administrator**:

```powershell
wsl.exe --install --no-distribution
```

Then restart Windows.

### Step 2: install Ubuntu
In a normal PowerShell window:

```powershell
wsl.exe --set-default-version 2
wsl.exe --install -d Ubuntu-24.04
```

Choose a Linux username and password when prompted.

### Step 3: limit WSL memory
Create `C:\Users\deban\.wslconfig`, because without a limit WSL can use too much of this machine's memory:

```ini
[wsl2]
memory=3GB
swap=4GB
```

Then run `wsl.exe --shutdown`.

### Step 4: install Docker Engine inside Ubuntu

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Membership of the `docker` group is effectively root access inside this WSL distribution. That's acceptable on a personal development machine.

Close and reopen Ubuntu, then verify:

```bash
docker run --rm hello-world
docker compose version
```

### Step 5: install JDK 25 inside Ubuntu
Integration tests would run inside WSL, so a Linux JDK is needed. The exact command would be confirmed if this route is taken.

## 3. Local addresses

| Service | Address |
|---|---|
| Vite dev server | `http://localhost:5173` |
| Backend with the `dev` profile | `http://localhost:8081` |
| Local PostgreSQL service | `localhost:5432` |
| Full stack through nginx (requires Docker) | `http://localhost:8080` |
| Compose PostgreSQL, from the host (requires Docker) | `localhost:5433` |

Compose ports are bound to `127.0.0.1` only, so nothing is exposed on the local network.

## 4. Demo environment topology

Chosen in Phase 3 (`DECISIONS.md`, D-033). Every part is on a free plan. The browser sees one origin, as with nginx:

```text
Browser
   |  HTTPS
Cloudflare Pages  (https://<project>.pages.dev)
   |-- /                         static React build, headers from public/_headers
   |-- /api/*                    \
   |-- /oauth2/authorization/*    |  Pages Functions (frontend/functions, edge/proxy.ts)
   |-- /login/oauth2/*            |  add X-Proxy-Secret and X-Client-IP
   |-- /actuator/health          /
   |  HTTPS
Render web service, free plan (Docker, backend/Dockerfile)
   |-- Neon PostgreSQL 18, free plan (direct connection, TLS)
   |-- Cloudflare R2, private bucket (S3 API)
   |-- Brevo email API (HTTPS, because Render's free plan blocks SMTP ports)
   |-- Gemini API (optional)
```

- **One origin.** The session cookie stays first-party with `SameSite=Lax`, so no CORS and no cookie changes are needed.
- **The backend's own address is public** (`*.onrender.com`). With `PROXY_SECRET` set, it refuses every request without that header, except `/actuator/health`, which Render's probe uses and which answers only UP or DOWN.
- **Visitor addresses.** Every request reaches the backend from Cloudflare, so the proxy sends the visitor's address (`CF-Connecting-IP`) as `X-Client-IP`, and rate limits use it. A browser can't supply either header, because the proxy replaces them.
- **Files** go to the private R2 bucket. Render's free plan has no persistent disk.

### Free-plan limits to know
| Service | Limit | What it means here |
|---|---|---|
| Render | Sleeps after 15 minutes without requests, and waking can take a minute or more. 512 MB memory, 0.1 CPU, 750 hours and 5 GB outbound bandwidth a month. | Open the app a few minutes before a demo. The Dockerfile limits the heap to fit. |
| Neon | 100 compute hours and 1 GB storage per project a month. Compute stops 5 minutes after the last query. | The backend queries every few seconds while awake, so Neon runs whenever Render does. **Don't use an uptime pinger** to keep Render awake: at 0.25 CU that would use the month's hours in about 16 days, and Neon would then stop until the next month. |
| Cloudflare R2 | 10 GB storage. Cloudflare may ask for a payment method before turning R2 on. | Far more than the demo needs. |
| Cloudflare Pages | Functions: 100,000 requests a day. Static files don't count. | Enough for demos. |
| Brevo | 300 emails a day. A Gmail sender address is replaced by one of Brevo's, so emails may land in spam. | A domain of your own, verified in Brevo, would fix delivery later. |

This setup is for synthetic demo data only. Real health data needs a separate production decision.

## 5. Configuration

| Variable | Purpose | Secret |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` or `demo` | No |
| `DB_URL`, `DB_USERNAME` | Database connection | No |
| `DB_PASSWORD` | Database password | Yes |
| `SESSION_COOKIE_SECURE` | Default `true`. Set to `false` only for local plain HTTP. Must stay `true` in demo. | No |
| `DOCUMENT_STORAGE_DIR` | Private folder for uploaded originals and photos (`DECISIONS.md`, D-020). Required unless `STORAGE_BUCKET` is set. The Docker image sets it to a volume. | No |
| `STORAGE_BUCKET` | When set, files go to this private S3-compatible bucket (Cloudflare R2 in the demo, D-033) instead of the folder | No |
| `STORAGE_ENDPOINT` | The S3 address, for R2 `https://<account-id>.r2.cloudflarestorage.com`. Required with a bucket. | No |
| `STORAGE_REGION` | Default `auto`, which R2 expects | No |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | An R2 API token limited to this bucket, with object read and write. Required with a bucket. | Yes |
| `PROXY_SECRET` | Hosted demo only (D-033). The backend refuses requests without this value in `X-Proxy-Secret`, except the health check, and takes the visitor's address from `X-Client-IP`. At least 32 characters (`openssl rand -hex 32`). The same value goes in Cloudflare Pages. Leave it empty everywhere else. | Yes |
| `BREVO_API_KEY` | When set, email goes through Brevo's HTTPS API instead of SMTP (D-033). `MAIL_FROM` must then be a sender address verified in Brevo. | Yes |
| `PORT` | Render only: the port it sends traffic to, `8080` | No |
| `JAVA_TOOL_OPTIONS` | Set in the Docker image to fit 512 MB (`-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k`). A host can override it. | No |
| `APP_PUBLIC_URL` | The address people open, such as `https://app.example.com`. Reset links and the Google return address are built from it. Default `http://localhost:5173`. | No |
| `MAIL_USERNAME` | Gmail address that sends reset emails. Without it, no email is sent (the `dev` profile writes the message to the log instead). | No |
| `MAIL_PASSWORD` | A Gmail **app password** for that address (not the normal password) | Yes |
| `MAIL_FROM` | Sender shown on emails, default `MAIL_USERNAME`. With Brevo, the sender verified there. | No |
| `MAIL_HOST`, `MAIL_PORT` | Default `smtp.gmail.com` and `587` | No |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID. Google sign-in is on only when this and the secret are set. | No |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret | Yes |
| `GEMINI_API_KEY` | AI provider key. Without it, AI features report unavailable and everything else works. | Yes |
| `GEMINI_MODEL` | Gemini model, default `gemini-3.5-flash` | No |
| `GEMINI_FALLBACK_MODEL` | Tried once when the main model reports high demand, default `gemini-flash-lite-latest`. Empty turns it off. | No |
| `AI_LIMIT_PER_ACCOUNT_PER_DAY`, `AI_LIMIT_GLOBAL_PER_DAY` | AI request limits over a rolling day, default 20 and 200 | No |
| `AI_PROVIDER_API_KEY` | AI provider (Phase 12) | Yes |
| `AI_DAILY_BUDGET_LIMIT` | Usage limit (Phase 12) | No |

`.env.example` lists names only. Real values go in `.env` locally, GitHub Actions secrets in CI, and the Render and Cloudflare dashboards for the demo environment. No secret variable may have a `VITE_` prefix, because those values are built into frontend code.

Cloudflare Pages has its own variables: `BACKEND_URL` (the Render address, such as `https://family-health-backend.onrender.com`), `PROXY_SECRET` (encrypted, the same value as the backend's), and `NODE_VERSION` (`22.14.0`, for the build).

## 6. CI/CD

1. **Pull request or push:** build and test (`07-testing-strategy.md` section 6). Runs from Phase 1.
2. **Backend deploy:** Render builds `backend/Dockerfile` from `main` after the GitHub checks pass (`render.yaml`, `autoDeployTrigger: checksPass`), and switches over once `/actuator/health` answers.
3. **Frontend deploy:** Cloudflare Pages builds `frontend` on every push to `main`. It can't wait for CI, so a broken frontend could go out before CI reports. Check the Actions result after each push.

## 7. Database migrations and backups

- Flyway runs automatically when the backend starts.
- A database backup is taken before any deployment that includes a migration.
- Backups are encrypted, kept off-server, and tested by restoring them.
- Rollback means redeploying the previous image. If a migration can't work with the previous version, restore from the backup taken before deployment.

## 8. Demo operations

- **Demo reset:** a command available only in the demo profile. It clears demo data and stored files, then loads the synthetic data set.
- **Before each presentation:** reset demo data, check the health endpoint, test login and one AI call, and confirm the demo banner is visible.
- **If AI is unavailable during a demo:** manual workflows still work, and pre-reviewed synthetic results can demonstrate the review screens.

## 9. Setting up the hosted demo (Phase 3b)

Done once, by the owner, in each service's dashboard. Push the Phase 3 code to GitHub first. Never put a secret in a file in the repository.

### Step 1: Neon database
1. Create a Neon account and a project: PostgreSQL **18**, region **AWS Asia Pacific (Singapore)**.
2. On the project dashboard, open **Connect**, turn **connection pooling off**, and copy the connection string. The app keeps its own pool, and Flyway needs a direct connection.
3. Turn `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require...` into three values:
   - `DB_URL` = `jdbc:postgresql://HOST/DBNAME?sslmode=require&channelBinding=require`
   - `DB_USERNAME` = `USER`
   - `DB_PASSWORD` = `PASSWORD`

### Step 2: Cloudflare R2 bucket
1. In the Cloudflare dashboard, open **R2 Object Storage**. Add a payment method if Cloudflare asks. The free 10 GB isn't charged.
2. **Create bucket**: name `family-health-demo`, location hint **Asia-Pacific**. Leave public access **off**.
3. **Manage API tokens**, then **Create API token**: permission **Object Read & Write**, for **this bucket only**.
4. Copy the **Access Key ID** (`STORAGE_ACCESS_KEY`), the **Secret Access Key** (`STORAGE_SECRET_KEY`, shown only once), and the **S3 endpoint** without the bucket name (`STORAGE_ENDPOINT`, `https://<account-id>.r2.cloudflarestorage.com`). `STORAGE_BUCKET` is `family-health-demo`.

### Step 3: Brevo email
1. Create a free Brevo account.
2. Under **Senders, Domains & Dedicated IPs**, open **Senders**, add your email address, and confirm the email Brevo sends. That address is `MAIL_FROM`.
3. Under **SMTP & API**, open **API Keys** and generate a key (`BREVO_API_KEY`).

### Step 4: the proxy secret
In Git Bash, run `openssl rand -hex 32`. The 64-character result is `PROXY_SECRET`, used in Steps 5 and 6.

### Step 5: Render backend
1. Create a Render account and connect GitHub, with access to this repository only.
2. **New**, then **Blueprint**, then choose this repository. Render reads `render.yaml`.
3. Fill in the values it asks for. For `APP_PUBLIC_URL`, use `https://<project>.pages.dev`, with the Pages project name you'll pick in Step 6. Leave `GOOGLE_*` and `GEMINI_API_KEY` empty if those features aren't wanted. Gemini's free tier is for synthetic data only (D-021).
4. Apply, and wait for the first deploy. The log should say `Requests must come through the proxy`, `Object storage bucket is reachable`, and `Brevo API key works`.
5. Note the service address, such as `https://family-health-backend.onrender.com`.

### Step 6: Cloudflare Pages frontend
1. **Workers & Pages**, then **Create**, then **Pages**, then **Connect to Git**, and choose this repository.
2. Project name: the one used in `APP_PUBLIC_URL`. Production branch `main`. Framework preset **None**. Build command `npm run build`. Build output directory `dist`. Root directory `frontend`.
3. Environment variables: `NODE_VERSION` = `22.14.0`, `BACKEND_URL` = the Render address from Step 5, and `PROXY_SECRET` (choose **Encrypt**).
4. Save and deploy. If the project name ended up different, update `APP_PUBLIC_URL` in Render.

### Step 7: Google sign-in (optional)
In Google Cloud Console, open the OAuth client and add the authorized redirect URI `https://<project>.pages.dev/login/oauth2/code/google`. Then set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in Render.

### Step 8: smoke test
With synthetic data only:
1. Open `https://<project>.pages.dev`. The first load after a pause can take a minute or more while Render wakes.
2. Register, sign out, and sign in again.
3. Create a profile, upload a document, open it, and delete it. Add a profile photo.
4. Use **Forgot password**, and check the inbox and the spam folder.
5. Turn on an emergency card and open its link in a private window.
6. Open `https://<render-service>.onrender.com/api/v1/auth/csrf` directly. It must answer **403** with `PROXY_REQUIRED`.
