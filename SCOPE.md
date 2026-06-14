# SplitSmart — Shared Expense Tracker

## 📌 Project Scope

### What This Application Does
SplitSmart is a production-ready shared expense tracking application built for flatmates and friend groups. It handles:

1. **Multi-user expense tracking** with JWT authentication
2. **4 split types**: Equal, Exact, Percentage, Shares
3. **Multi-currency support** (INR + USD with configurable static rate)
4. **CSV import pipeline** with 12-rule anomaly detection
5. **Balance calculation engine** with 7 business rules
6. **Settlement suggestions** using minimum-transactions algorithm
7. **Time-range group membership** (members can join/leave at specific dates)

### Core User Stories (from assignment)
| Person | Requirement | How We Solve It |
|--------|-------------|-----------------|
| **Aisha** | "One number per person. Who pays whom, how much, done." | `BalanceCalculationService` → `SettlementSuggestion` (minimum transactions algorithm) |
| **Rohan** | "No magic numbers. Show exactly which expenses make up my balance." | `ExpenseContribution` list in `UserBalance` — every balance links back to individual expenses |
| **Priya** | "Fix USD/INR mismatch." | `CurrencyConversionService` converts at import time; `ANOMALY_002` detects mismatches |
| **Meera** | "Must approve every change to shared data." | `ImportAnomaly` → `UserDecision` flow — nothing imported without explicit APPROVED/REJECTED |
| **Sam** | "Joined mid-April — don't charge me for February." | `GroupMembership.wasActiveOn(date)` filters participants by join/leave dates |
| **Dev** | "Some expenses in USD." | `Currency` enum + `amountInInr` field — all calculations normalized to INR |

### What Is NOT In Scope
- Real-time exchange rates (we use static, documented rates)
- Role-based access control (all authenticated users have equal permissions within groups)
- Push notifications
- Mobile native apps (web-only SPA)
- OAuth/social login (JWT email/password only)
- File storage for receipts/photos

### Architecture
```
Frontend (React + Vite)  →  Backend (Spring Boot 3.2)  →  PostgreSQL 16
     Port 3000                  Port 8080                  Port 5432
```

### Tech Stack
| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | React + Vite | React 18, Vite 5 |
| Backend | Spring Boot + JPA | 3.2.5 |
| Database | PostgreSQL | 16 |
| Auth | JWT (JJWT) | 0.12.5 |
| Migrations | Flyway | Managed by Spring Boot |
| CSV Parser | OpenCSV | 5.9 |
| Container | Docker + Docker Compose | Latest |

### Database Schema (9 tables, 5 ENUMs)
- `users` — authentication and identity
- `groups` — expense groups
- `group_memberships` — time-ranged membership (join/leave dates)
- `expenses` — shared expenses with multi-currency support
- `expense_splits` — per-user split amounts (4 split types)
- `payments` — settlement records
- `exchange_rates` — currency conversion rates
- `import_sessions` — CSV upload lifecycle tracking
- `import_anomalies` — detected data quality issues
