# 🧩 SplitSmart — Shared Expense Tracker

A production-ready shared expense tracking application for flatmates and friend groups. Built with **Spring Boot 3.2** (backend), **React 18 + Vite** (frontend), and **PostgreSQL 16** (database).

## ✨ Features

- **JWT Authentication** — Stateless, secure, with BCrypt password hashing
- **4 Split Types** — Equal, Exact Amount, Percentage, Shares
- **Multi-Currency** — INR + USD with configurable conversion rate
- **CSV Import Pipeline** — 12-rule anomaly detection with user approval workflow
- **Balance Engine** — 7 business rules, BigDecimal precision, minimum-transactions settlement
- **Time-Ranged Membership** — Members can join/leave at specific dates
- **Responsive UI** — Material Design 3 inspired, mobile-first

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Node.js 18+
- PostgreSQL 16+ (or Docker)
- Maven 3.9+

### 1. Start PostgreSQL (Docker)
```bash
docker-compose up postgres -d
```

### 2. Start Backend
```bash
cd backend
.\run-backend.cmd
```
Backend starts at `http://localhost:8080`

If you run `mvn spring-boot:run` directly, make sure `JAVA_HOME` points to Java 17.
Spring Boot 3.2 cannot run with Java 8.

To verify the Java version Maven is using:
```bash
mvn -version
```
The output must show Java 17. If it shows Java 8, use `.\run-backend.cmd` or fix your system `JAVA_HOME`.

### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
Frontend starts at `http://localhost:3000`

### Full Stack (Docker Compose)
```bash
docker-compose up -d
```
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

## 📁 Project Structure

```
SharedExpenseTracker/
├── backend/                    # Spring Boot 3.2
│   ├── src/main/java/com/spreetail/expenses/
│   │   ├── auth/               # JWT auth (filter, service, controller)
│   │   ├── balance/            # Balance calculation engine (7 rules)
│   │   ├── common/             # ApiResponse, exception handlers
│   │   ├── currency/           # Currency conversion service
│   │   ├── expense/            # Expense + ExpenseSplit (4 split types)
│   │   ├── group/              # Group + GroupMembership (time-ranged)
│   │   ├── importer/           # CSV import pipeline (12 anomaly rules)
│   │   ├── settlement/         # Payment/Settlement module
│   │   └── user/               # User entity + service
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway V1-V6 migrations
│   │   └── application.properties
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                   # React 18 + Vite
│   ├── src/
│   │   ├── components/         # AppLayout (shell)
│   │   ├── context/            # AuthContext (JWT state)
│   │   ├── pages/              # Login, Register, Dashboard, Groups, GroupDetail
│   │   ├── services/           # API client (axios + interceptors)
│   │   ├── App.jsx             # Router with protected routes
│   │   ├── index.css           # Full design system (MD3 tokens)
│   │   └── main.jsx            # Entry point
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docker-compose.yml          # Full stack deployment
├── SCOPE.md                    # What's in/out of scope
├── DECISIONS.md                # Technical decisions & rationale
├── AI_USAGE.md                 # AI usage disclosure
└── .env.example                # Environment variable template
```

## 🔌 API Endpoints

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns JWT |

### Groups
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups` | List all groups |
| POST | `/api/groups` | Create group |
| GET | `/api/groups/{id}` | Get group details |
| POST | `/api/groups/{id}/members` | Add member |

### Expenses
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups/{id}/expenses` | List expenses |
| POST | `/api/groups/{id}/expenses` | Create expense (4 split types) |

### Balances
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups/{id}/balances` | Calculate all balances + settlements |

### Payments
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups/{id}/payments` | List payments |
| POST | `/api/groups/{id}/payments` | Record settlement |

### CSV Import
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/groups/{id}/import/upload` | Upload CSV, detect anomalies |
| POST | `/api/import/{session}/decisions` | Submit approve/reject decisions |
| POST | `/api/groups/{id}/import/{session}/confirm` | Confirm import |
| GET | `/api/import/{session}/report` | View import report |

## 🧮 Balance Calculation Rules

1. **RULE 1**: Membership date filter — only active members on expense date
2. **RULE 2**: Currency normalization — all calculations in INR (amountInInr)
3. **RULE 3**: Split type handling — EQUAL, EXACT, PERCENTAGE, SHARES
4. **RULE 4**: Settlement deduction — payments reduce outstanding balances
5. **RULE 5**: Rounding — BigDecimal HALF_UP at scale 2
6. **RULE 6**: Traceability — every balance links to individual expenses
7. **RULE 7**: Minimum transactions — greedy algorithm for settlement suggestions

## 🔍 CSV Anomaly Detection (12 Rules)

| # | Anomaly | Policy |
|---|---------|--------|
| 001 | Duplicate expense | Flag for user approval |
| 002 | Currency mismatch (USD as INR) | Flag, offer conversion |
| 003 | Negative amount | Treat as refund, flag |
| 004 | Post-exit expense | Exclude member, flag |
| 005 | Settlement as expense | Import as Payment |
| 006 | Missing required fields | Skip row |
| 007 | Unknown member name | Pause, ask user |
| 008 | Conflicting duplicate | Flag both rows |
| 009 | Invalid date format | Try multiple formats |
| 010 | Split % ≠ 100 | Block import |
| 011 | Unsupported split type | Skip row |
| 012 | Zero amount | Skip row |

## 📄 License

MIT
