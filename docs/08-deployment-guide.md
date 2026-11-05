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

The frontend and API share one origin:

```text
Browser
   |  HTTPS
Reverse proxy with TLS
   |-- /            static React build
   |-- /api/*       Spring Boot backend
   |-- /actuator/health
   |
Spring Boot backend
   |-- PostgreSQL
   |-- Private S3-compatible object storage
   |-- AI provider (outbound only)
```

Only ports 80 and 443 are public. PostgreSQL and the backend are never publicly reachable.

### Hosting choice (Phase 3 decision)
The recommended starting point is one small VPS running Docker Compose, with a low-cost PostgreSQL arrangement and off-server encrypted backups. The specific host, storage provider, domain, and monthly budget are still open.

## 5. Configuration

| Variable | Purpose | Secret |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` or `demo` | No |
| `DB_URL`, `DB_USERNAME` | Database connection | No |
| `DB_PASSWORD` | Database password | Yes |
| `SESSION_COOKIE_SECURE` | Default `true`. Set to `false` only for local plain HTTP. Must stay `true` in demo. | No |
| `STORAGE_ENDPOINT`, `STORAGE_BUCKET` | Object storage (Phase 11) | No |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | Object storage (Phase 11) | Yes |
| `AI_PROVIDER_API_KEY` | AI provider (Phase 12) | Yes |
| `AI_DAILY_BUDGET_LIMIT` | Usage limit (Phase 12) | No |

`.env.example` lists names only. Real values go in `.env` locally, GitHub Actions secrets in CI, and server environment variables for the demo environment. No secret variable may have a `VITE_` prefix, because those values are built into frontend code.

## 6. CI/CD

1. **Pull request or push:** build and test (`07-testing-strategy.md` section 6). Runs from Phase 1.
2. **Merge to `main`:** build and publish images (Phase 3).
3. **Deploy to demo:** pull images, then restart with health checks (Phase 3).

## 7. Database migrations and backups

- Flyway runs automatically when the backend starts.
- A database backup is taken before any deployment that includes a migration.
- Backups are encrypted, kept off-server, and tested by restoring them.
- Rollback means redeploying the previous image. If a migration can't work with the previous version, restore from the backup taken before deployment.

## 8. Demo operations

- **Demo reset:** a command available only in the demo profile. It clears demo data and stored files, then loads the synthetic data set.
- **Before each presentation:** reset demo data, check the health endpoint, test login and one AI call, and confirm the demo banner is visible.
- **If AI is unavailable during a demo:** manual workflows still work, and pre-reviewed synthetic results can demonstrate the review screens.

## 9. Open for Phase 3

- **Q1.** What monthly hosting and AI budget can the demo environment have?
- **Q2.** Do you already have a domain, and is there a date the demo needs to be online by?
