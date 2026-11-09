

# Family Health Companion
## Software Requirements Specification — MVP v0.3

**Status:** Approved MVP baseline  
**Purpose:** Define the first usable release of Family Health Companion. This document is the reference for system design, implementation, and testing.

## 1. Product Overview

Family Health Companion helps individuals and families securely organize health records, track health-related information, and access AI-assisted meal suggestions.

The application centralizes medical documents, prescriptions, medication information, symptoms, food logs, and lab results in a chronological health history.

It is a health-record management and support tool—not a diagnostic or treatment system.

## 2. Target Users

- Individuals managing their own health information.
- Family members managing records for dependents or elderly relatives.
- Adult family members who want access to their own records through separate accounts.

## 3. MVP Scope

### 3.1 Family and Profile Management
- Register and authenticate users.
- Create and manage family member profiles.
- Support dependent profiles managed by a family manager.
- Allow optional separate accounts for adult family members.
- Apply access permissions so users can access only authorized profiles and records.

### 3.2 Health Records
- Record known medical conditions and relevant history.
- Record allergies, dietary restrictions, food preferences, and emergency contacts for each profile.
- Record doctor visits, symptoms, medications, and treatment information as reported or documented.
- Upload and organize PDFs, images, scans, lab reports, and prescriptions.
- Preserve original uploaded documents. The system never removes or alters an original on its own, including when extraction fails.
- Allow users to explicitly delete their records and uploaded documents.
- Link extracted information to its source document.
- Support manual entry and editing.

### 3.3 AI-Assisted Document Processing
- Extract information from supported documents.
- ~~Transcribe user-provided voice notes into text.~~ Deferred by the owner on 2026-11-09 (`DECISIONS.md`, D-022). May return later.
- Show extracted information as a draft for user review and correction.
- Save extracted data as confirmed records only after user approval.
- If extraction is unreliable, retain the original document without inventing structured data.

### 3.4 Health Timeline and Summaries
- Display records chronologically.
- Show recorded conditions, symptoms, visits, medications, and lab results.
- Provide summaries based only on available records.
- Identify missing, outdated, or conflicting information where possible.
- Distinguish user-reported information, documented records, and AI-generated summaries. "Documented" means taken from an attached document, not that the document has been verified.

### 3.5 Medication Tracking
- Store medication name, dosage as recorded, schedule, and relevant dates.
- Allow users to track whether they took a medication.
- Support reminders.
- Do not recommend medication changes or create prescriptions.

### 3.6 Lab Results
- Store lab values with test name, value, unit, date, and source where available.
- Display basic historical trends for comparable values.
- Avoid interpreting results as a diagnosis.
- Clearly indicate when values, units, or dates are missing, and never guess them.
- Exclude values with a missing unit or date from trends.

### 3.7 Food and Symptom Tracking
- Allow users to log meals and symptoms manually.
- Include date and time where available.
- Display entries in the user’s timeline.
- Do not claim that a food caused a symptom based only on recorded associations.

### 3.8 AI Meal Assistant
The assistant shall provide two modes:

**General mode**
- Generate recipes from text-entered ingredients. Voice entry is deferred (`DECISIONS.md`, D-022).
- Consider cooking time and preferences when provided.
- Respect allergies explicitly stated in the current request.
- Does not automatically use a selected family member’s health profile.

**Health-aware mode**
- Requires selecting a family member.
- Considers that member’s recorded allergies, dietary restrictions, relevant health information, and preferences.
- Discloses when important dietary information is missing or uncertain.
- Does not claim a recipe is medically safe or prescribe a therapeutic diet.

**Shared behavior**
- Provide a small set of recipe suggestions with ingredients and preparation steps.
- Allow users to switch modes without re-entering ingredients.
- Allow users to save a selected recipe or meal to their food log.
- Treat AI output as suggestions, not medical advice.

### 3.9 Emergency Health Card
- Provide an optional emergency card accessible through a user-enabled link or QR code.
- Include only information explicitly approved by the user, such as emergency contact, known allergies, critical medications, and major recorded conditions.
- Do not expose complete medical history or private documents.
- Allow the user to update or disable access.
- Clearly warn that anyone with the link or QR code may be able to view the card.

### 3.10 PDF Export
- Allow users to export a selected health summary.
- Include source dates and relevant record details.
- Label the export as a user-generated health record summary, not an official medical certificate.

## 4. Medical and AI Safety Requirements

The application and its AI features must:

- Not diagnose diseases or predict future health outcomes.
- Not prescribe, stop, or change medication.
- Not declare a person healthy based on incomplete or normal records.
- Summarize only information present in available records or explicitly provided by the user.
- Preserve dates, units, and source information when available.
- Avoid inventing missing information.
- Clearly distinguish documented facts, user-reported information, and AI-generated content.
- Require user review before AI-extracted data becomes a confirmed record.
- Encourage consultation with a qualified healthcare professional for medical decisions or concerning symptoms.

## 5. Privacy and Security Requirements

- Authenticate users before accessing private records.
- Enforce authorization at the backend for every profile and record operation.
- Support family-member-specific access permissions.
- Protect sensitive health data in transit and at rest.
- Store uploaded documents securely and restrict access to authorized users.
- Keep emergency-card access separate from private account access.
- Record relevant security-sensitive actions in an audit trail.
- Do not sell health data or use it for targeted advertising.

## 6. Nonfunctional Requirements

- Use a modular architecture that supports future feature expansion.
- Keep business rules out of controllers and UI components.
- Validate user input on the backend.
- Provide meaningful error responses and application logs.
- Maintain automated tests for core business logic and access control.
- Ensure AI failures do not prevent users from manually managing records.
- Design the system so additional languages can be introduced later.

## 7. MVP Use Cases

1. A user registers and creates a family profile.
2. A user manually records a medical condition, visit, medication, or symptom.
3. A user uploads a report or prescription and reviews extracted information.
4. A user views a family member’s chronological health timeline.
5. A user tracks medication adherence and receives reminders.
6. A user views historical lab values.
7. A user logs meals and symptoms.
8. A user requests a general recipe from available ingredients.
9. A user selects a family member and requests health-aware recipes.
10. A user generates a record-based health summary.
11. A user creates, shares, updates, or disables an emergency card.
12. A user exports a health summary as PDF.

## 8. MVP Exclusions

- Disease diagnosis or prediction.
- Medication prescribing or treatment recommendations.
- Hospital, laboratory, insurance, or wearable integrations.
- Advanced clinical decision support.
- Full offline access.
- Official medical certificates or verification of medical documents.
- Advanced analytics claiming causal relationships between food and symptoms.

## 9. Acceptance Criteria

The MVP is acceptable when:

- Users can securely manage their own and authorized family members’ profiles.
- Core records can be created manually and viewed chronologically.
- Documents can be uploaded and retrieved without loss of the original.
- AI-extracted information requires user approval before confirmation.
- Unauthorized users cannot access private records.
- General and Health-aware meal modes work as specified.
- Health summaries remain grounded in available records and avoid diagnosis.
- Emergency-card access is opt-in and limited to approved information.
- PDF exports are clearly labeled as record summaries.
- Core workflows have automated tests and documented setup instructions.

