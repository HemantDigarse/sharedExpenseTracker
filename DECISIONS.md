# SplitSmart — Technical Decisions

## 1. Flyway vs Hibernate ddl-auto
**Decision**: Flyway with `ddl-auto=validate`

**Why**: Hibernate's `create`/`update` modes are unpredictable in production — they can drop columns, add unintended constraints, or fail silently. Flyway gives us:
- Versioned, reviewable SQL migrations (V1 through V6)
- Reproducible deployments (same migration sequence everywhere)
- Safe rollback path (each migration is tracked in `flyway_schema_history`)
- Full control over PostgreSQL-specific features (ENUMs, CHECK constraints, partial indexes)

## 2. BigDecimal for All Money
**Decision**: `BigDecimal` with `RoundingMode.HALF_UP` at scale 2

**Why**: Floating-point arithmetic (`double`/`float`) introduces rounding errors. Example:
```
0.1 + 0.2 = 0.30000000000000004  (double)
0.1 + 0.2 = 0.3                   (BigDecimal)
```
In a financial application, even ₹0.01 discrepancies compound across hundreds of transactions. BigDecimal with explicit rounding mode eliminates this class of bugs entirely.

## 3. Static USD→INR Rate (Not Real-Time)
**Decision**: Configurable static rate, default `83.00`

**Why**: The assignment data is historical (Feb–May). Using a real-time API would:
- Give different results each time balances are calculated
- Make balance verification impossible ("why did my balance change overnight?")
- Add an external dependency that could fail

A static, documented rate ensures consistency and auditability. The rate is configurable via `currency.usd-to-inr-rate` in `application.properties`.

## 4. JWT Stateless Auth (No Sessions)
**Decision**: Stateless JWT with `JwtAuthenticationFilter`

**Why**: 
- No server-side session storage needed
- Horizontally scalable (any backend instance can validate)
- Clean separation between frontend and backend
- CSRF protection unnecessary (no cookies)

**Trade-off**: Token revocation requires a blacklist (not implemented — token expiry handles this).

## 5. Time-Ranged Group Membership
**Decision**: `joined_at` + `left_at` on `GroupMembership`

**Why**: The assignment explicitly requires:
- "Sam joined mid-April — don't charge for February"
- "Meera left end of March"

A simple boolean `active` flag can't answer "was this person active on March 15th?" The `wasActiveOn(LocalDate)` method on `GroupMembership` handles this cleanly.

## 6. Anomaly Detection Over Auto-Correction
**Decision**: Flag all anomalies, never auto-correct

**Why**: Meera's requirement: "must approve every change." Auto-correction (e.g., silently converting USD to INR) would violate this. Instead:
1. `AnomalyDetector` detects 12 anomaly types
2. Each anomaly is persisted with the raw CSV row
3. User reviews each anomaly: APPROVE or REJECT
4. Only after ALL anomalies are resolved does the import proceed
5. The entire import is a single `@Transactional` — partial imports are impossible

## 7. Pre-Calculated Split Amounts
**Decision**: `finalAmountOwed` computed at expense creation time

**Why**: Calculating splits on-the-fly during balance queries would:
- Make balance queries O(n × m) where n=expenses, m=members
- Risk inconsistency if split logic changes
- Make debugging harder ("why does this balance not match?")

By storing `finalAmountOwed` at creation time, balance calculation becomes a simple SUM query. The split type (EQUAL/EXACT/PERCENTAGE/SHARES) is handled once at write time.

## 8. Minimum Transactions Algorithm
**Decision**: Greedy matching for settlement suggestions

**Why**: The optimal "minimum transactions" problem is NP-hard for large groups. But for flat-sharing (4-6 people), the greedy approach:
1. Separate into debtors and creditors
2. Match largest debtor with largest creditor
3. Transfer min(debt, credit)
4. Repeat

This produces optimal or near-optimal results for groups ≤ 10 people, which covers all real-world flat-sharing scenarios.

## 9. React SPA (Not Server-Rendered)
**Decision**: Vite + React SPA with API proxy

**Why**: 
- The backend is a pure REST API — no server rendering needed
- Vite provides instant HMR for development
- SPA routing enables smooth page transitions
- Nginx serves the built frontend in production

## 10. Layered Architecture (DTOs Only at Boundaries)
**Decision**: Entity → Service → DTO → Controller

**Why**: Entities should never leak to the API layer because:
- Circular references (JPA lazy loading → infinite JSON serialization)
- Security (password hash, internal IDs exposed)
- Flexibility (API shape can evolve independently of schema)

Every service method returns DTOs. Every controller accepts request DTOs.
