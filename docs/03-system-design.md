

# Family Health Companion
## System Design — Prototype v0.2

**Status:** Approved baseline  
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
- Minimal audit log of security-sensitive actions.
- Designed to support stronger authentication later.

### Family
- Family groups.
- Family member profiles.
- Family manager and member relationships.
- Authorization rules for profile access.

### Health Records
- Medical history.
- Allergies, dietary restrictions, food preferences, and emergency contacts.
- Doctor visits.
- Symptoms, whether part of a visit or logged on their own.
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

### Food Log
- Meal logs.
- Chronological display.

Symptoms belong to Health Records. The SRS groups food and symptom tracking from the user's point of view, and the two still appear together on the timeline.

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
| Voice transcription | Deferred (`DECISIONS.md`, D-022) |
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
- Provide a way to delete demo records and uploaded files. This is separate from user deletion: only an explicit action removes data, and the system never removes originals on its own.
- Record security-sensitive actions in a minimal audit log.
- Do not use real health data in public demos.
- Label AI-generated summaries and extracted data clearly.

Production hardening later includes a formal security review, stronger account recovery, advanced audit reporting, retention policies, tested disaster recovery, and privacy/legal review.

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

This list shows the order of work. `PROJECT_STATUS.md` keeps the original phase numbers, where deployment is Phase 3 and runs after Phase 5. Tests are written within each phase, and CI runs from Phase 1. Each phase requires approval before it starts. `PROJECT_STATUS.md` tracks progress.

1. Repository foundation and minimal end-to-end app.
2. Design baseline documents.
3. Identity, sessions, and audit log.
4. Family, profiles, and authorization.
5. Deploy online with HTTPS.
6. Conditions, allergies, restrictions, emergency contacts, and record provenance.
7. Visits, symptoms, and first timeline.
8. Medication, adherence, and reminders.
9. Lab results and trends.
10. Food log.
11. Document upload and private storage.
12. AI layer, then extraction with review and approval.
13. ~~Voice transcription.~~ Deferred (`DECISIONS.md`, D-022).
14. General meal assistant.
15. Health-aware meal assistant.
16. Record-based summaries.
17. Emergency card.
18. PDF export.
19. Synthetic demo data, security review, and presentation preparation.

Medication and lab records come before extraction because approved drafts have to become those records. Database tables arrive through Flyway migrations with the phase that needs them.

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

