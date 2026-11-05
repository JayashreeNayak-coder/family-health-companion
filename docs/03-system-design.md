

# Family Health Companion
## System Design — Prototype v0.1

**Status:** Proposed baseline  
**Delivery goal:** Online, functional prototype for college submission, teacher review, and client demonstrations.  
**Long-term goal:** Evolve the same codebase into a secure, sellable family health management product.

## 1. Architecture Decision

Use a **modular monolith**.

The frontend and backend are separate applications, but the backend is one deployable Spring Boot application organized into business modules.

```text
                   User
                    |
              React Web App
            React + TypeScript
                    |
                HTTPS / REST
                    |
           Spring Boot Backend
                    |
    ---------------------------------
    |        |        |             |
  Family   Records  Documents   AI Services
    |        |        |             |
    ---------------------------------
                    |
       ---------------------------
       |                         |
   PostgreSQL             Private File Storage
       |
  Persistent App Data
```

**Why this architecture:**
- Simple and affordable to deploy.
- Clear module boundaries for maintainability.
- Avoids premature microservices and Kubernetes.
- Allows modules to be extracted later if real scale requires it.
- Suitable for a solo developer using AI coding assistance.

## 2. Technology Stack

| Component | Technology |
|---|---|
| Frontend | React + TypeScript |
| Build tool | Vite |
| Styling | Tailwind CSS |
| Backend | Java + Spring Boot |
| API | REST |
| API documentation | OpenAPI / Swagger |
| Database | PostgreSQL |
| Persistence | Spring Data JPA |
| Schema migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Security | Spring Security |
| File storage | S3-compatible private object storage |
| AI integration | Backend AI service layer |
| Document extraction | OCR / document processing service |
| Testing | JUnit, Mockito, Testcontainers, frontend tests |
| Containerization | Docker |
| CI/CD | GitHub Actions |
| Hosting | Low-cost cloud/VPS for prototype |

## 3. Backend Modules

Each module should own its entities, repositories, services, controllers, and DTOs where appropriate.

### Identity and Access
- Prototype login and user accounts.
- Role and profile access checks.
- Designed to support stronger authentication later.

### Family
- Family groups.
- Family member profiles.
- Family manager and member relationships.
- Authorization rules for profile access.

### Health Records
- Medical history.
- Doctor visits.
- Symptoms.
- Health timeline.
- Record summaries.

### Documents
- Upload and retrieve reports, prescriptions, and scans.
- Document metadata and storage references.
- AI extraction status.
- Link extracted information to source documents.

### Medication
- Medication records and schedules.
- Adherence tracking.
- Reminder configuration.

### Lab Results
- Test name, value, unit, date, and source.
- Basic historical trend data.

### Food and Symptoms
- Meal logs.
- Symptom logs.
- Chronological display.

### Meal Assistant
- General recipe mode.
- Health-aware mode for a selected profile.
- Recipe generation and saving.
- AI safety checks and usage limits.

### Emergency Card
- User-approved limited emergency information.
- Public-link/QR demo capability.
- Enable/disable control.

### AI Services
A shared backend layer for AI calls:
- Document extraction.
- Voice transcription.
- Record-based summaries.
- Recipe generation.

The AI provider must not be called directly from frontend code. API keys stay on the backend.

## 4. Prototype Feature Classification

| Feature | Prototype behavior |
|---|---|
| User and family profiles | Working, persistent |
| Manual health record entry | Working, persistent |
| Timeline | Working, based on database records |
| Document upload | Working with private storage |
| AI document extraction | Working, review before saving |
| Medication tracking | Working |
| Lab history | Working, basic trends |
| Food/symptom logs | Working |
| General meal assistant | Working with usage limits |
| Health-aware meal assistant | Working with selected profile data |
| Health summaries | Working, record-grounded |
| Emergency card | Demo-limited, opt-in |
| PDF export | Basic summary export |
| Payments/subscriptions | Not implemented |
| Hospital integrations | Not implemented |
| Advanced analytics | Not implemented |
| Offline mode | Not implemented |

## 5. Key Data Flow

### Document upload and extraction

```text
User selects file
      |
Backend validates file
      |
File stored privately
      |
Document metadata saved in PostgreSQL
      |
Extraction job processes document
      |
AI returns structured draft
      |
User reviews and edits
      |
User approves
      |
Confirmed record saved and linked to source
```

The original file remains available even if extraction fails.

### Health-aware meal assistant

```text
User selects Health-aware mode
      |
Select family member
      |
Backend loads relevant approved profile data
      |
User enters ingredients
      |
AI generates recipe suggestions
      |
Backend checks known restrictions/allergies
      |
Results shown with limitations
      |
User may save meal to food log
```

Do not send the member’s entire medical history to the AI. Send only relevant information required for the requested feature.

## 6. Prototype Deployment

```text
GitHub Repository
       |
GitHub Actions
       |
Build and test
       |
Deploy frontend + backend
       |
Hosted PostgreSQL
       |
Private object storage
```

### Prototype deployment principles
- Use one backend service initially.
- Host frontend separately or serve it through the same deployment.
- Use a managed PostgreSQL service if affordable; otherwise use PostgreSQL on a VPS with off-server backups.
- Store uploaded files in private object storage, not in a public folder.
- Use HTTPS.
- Keep development, demo, and production configuration separate.
- Use environment variables or secret management for credentials.
- Use synthetic demo data for presentations.

## 7. Prototype Security Minimum

Even for a college prototype:

- Validate uploads by file type and size.
- Keep documents private.
- Enforce profile authorization in backend services.
- Never expose AI API keys in frontend code.
- Use HTTPS.
- Avoid committing secrets to Git.
- Provide a way to delete demo records and uploaded files.
- Do not use real health data in public demos.
- Label AI-generated summaries and extracted data clearly.

Production hardening later includes a formal security review, stronger account recovery, audit controls, retention policies, tested disaster recovery, and privacy/legal review.

## 8. Repository Structure

```text
family-health-companion/
├── frontend/
├── backend/
├── docs/
│   ├── 01-problem-statement.md
│   ├── 02-srs.md
│   ├── 03-system-design.md
│   ├── 04-database-design.md
│   ├── 05-api-specification.md
│   ├── 06-security-and-ai-safety.md
│   ├── 07-testing-strategy.md
│   └── 08-deployment-guide.md
├── docker-compose.yml
├── README.md
├── CLAUDE.md
├── PROJECT_STATUS.md
├── AI_CONTEXT.md
└── DECISIONS.md
```

## 9. Implementation Sequence

1. Repository setup and development environment.
2. Database design and Flyway migrations.
3. Identity, family, and profile access.
4. Health records and timeline.
5. Document upload and private storage.
6. AI extraction with user approval.
7. Medication and lab records.
8. Food and symptom logging.
9. General and health-aware meal assistant.
10. Health summary and emergency card.
11. PDF export.
12. Testing, demo data, deployment, and college presentation preparation.

## 10. Deferred Until Product Validation

- Subscription/payment integration.
- Hospital and laboratory integrations.
- Wearables.
- Multi-service architecture.
- Kubernetes.
- Advanced analytics.
- Large-scale monitoring and operations.
- Full offline support.

## 11. Product Evolution Path

The prototype should use clean interfaces around storage, AI providers, authentication, and notifications. That allows these services to be replaced or upgraded without rewriting the core health-record modules.

The prototype is not intended to be instantly production-certified. It is intended to be a **real, maintainable application with a controlled demo environment**, which can be hardened after user feedback and product validation.

