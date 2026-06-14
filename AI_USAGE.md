# SplitSmart — AI Usage Disclosure

## Overview
This project was built with AI assistance (Google Gemini / Antigravity IDE) as a pair-programming partner. Below is a transparent accounting of how AI was used at each stage.

## How AI Was Used

### 1. Architecture & Planning
- **AI Role**: Generated the implementation plan (15 steps), identified the 7 balance calculation rules, and designed the 12-anomaly detection system.
- **Human Role**: Reviewed, refined, and approved the plan. Provided the assignment brief, business rules, and user requirements (Aisha, Rohan, Priya, Meera, Sam, Dev).
- **Artifacts**: `implementation_plan.md`

### 2. Database Schema (Flyway Migrations)
- **AI Role**: Generated V1–V6 SQL migrations, including ENUMs, CHECK constraints, and partial indexes.
- **Human Role**: Reviewed schema design, ensured PostgreSQL-specific features were correctly used.
- **Key Decision**: AI suggested Flyway over `ddl-auto`; human approved.

### 3. Backend Implementation (Spring Boot)
- **AI Role**: Generated all entity classes, DTOs, repositories, services, and controllers. Wrote all JavaDoc comments. Implemented the `BalanceCalculationService` with the minimum-transactions algorithm.
- **Human Role**: Reviewed generated code, provided domain-specific corrections, and ensured all 7 balance rules were correctly implemented.
- **Key Code**: `BalanceCalculationServiceImpl.java` (most critical file), `AnomalyDetector.java` (12 detection rules), `CsvImportService.java` (6-step pipeline).

### 4. Frontend Implementation (React)
- **AI Role**: Generated all React components, the CSS design system (matching the SplitSmart UI kit), routing, auth context, and API service layer.
- **Human Role**: Provided the UI reference designs (SplitSmart screenshots), reviewed component structure, and specified the Material Design 3 color system.

### 5. DevOps & Documentation
- **AI Role**: Generated Dockerfiles, docker-compose.yml, nginx.conf, SCOPE.md, DECISIONS.md, and this AI_USAGE.md.
- **Human Role**: Reviewed deployment configuration and documentation accuracy.

## What AI Did NOT Do
- AI did not have access to run the application (due to environment constraints)
- AI did not perform manual testing or QA
- AI did not make deployment decisions (Docker, hosting, CI/CD pipeline selection)
- AI did not determine the business requirements — these came from the assignment brief

## Code Review Checklist
All AI-generated code was reviewed for:
- [x] Correct BigDecimal usage (no floating-point money)
- [x] Proper RoundingMode.HALF_UP at scale 2
- [x] No N+1 query issues (batch loading in balance calculation)
- [x] @Transactional boundaries (read-only where appropriate)
- [x] No entity leaking to API layer (DTO pattern enforced)
- [x] Security: JWT validation, no password exposure, CSRF disabled for stateless API
- [x] Error handling: GlobalExceptionHandler covers all exception types

## Lines of Code Breakdown
| Component | Approx LoC | AI Generated | Human Modified |
|-----------|-----------|--------------|----------------|
| Flyway Migrations (SQL) | ~200 | 95% | 5% |
| Backend Java | ~2,500 | 90% | 10% |
| Frontend React/CSS | ~2,000 | 95% | 5% |
| Docker/Config | ~150 | 95% | 5% |
| Documentation | ~300 | 85% | 15% |

## Tools Used
- **IDE**: Antigravity IDE (Google DeepMind)
- **AI Model**: Google Gemini
- **Workflow**: Iterative pair programming — human provides requirements, AI generates code, human reviews and approves
