-- ==============================================================
-- V5: CREATE IMPORT TABLES + ADD DEFERRED FK ON EXPENSES
-- ==============================================================
-- Three things happen in this migration:
--
-- 1. Create 'import_sessions' table — one row per CSV upload.
--    Tracks the upload lifecycle: PENDING → REVIEWING → COMPLETE/FAILED.
--
-- 2. Create 'import_anomalies' table — one row per detected anomaly.
--    This is the backbone of Meera's requirement: "Show me duplicates
--    before deleting — I must approve every change."
--    Each anomaly is flagged with a type (one of 12+ types),
--    a description, and a user decision (PENDING/APPROVED/REJECTED).
--
-- 3. Add the deferred foreign key from expenses.import_session_id
--    to import_sessions(id). This FK couldn't be added in V3
--    because import_sessions didn't exist yet (migration ordering).
--
-- PostgreSQL ENUMs for import_status, anomaly_type, and user_decision
-- enforce valid values at the database level.
-- ==============================================================

-- ENUM: Import session lifecycle states.
-- PENDING:   file uploaded, not yet parsed
-- REVIEWING: anomalies detected, waiting for user approval
-- COMPLETE:  user approved, data written to DB
-- FAILED:    import failed (parse error, DB write error, etc.)
CREATE TYPE import_status AS ENUM ('PENDING', 'REVIEWING', 'COMPLETE', 'FAILED');

-- ENUM: All 12 known anomaly types from the CSV import pipeline.
-- Each type maps to a specific detection rule in AnomalyDetector.java.
-- See the import pipeline specification for detailed descriptions.
--
-- DUPLICATE_EXPENSE:          Same description + date + amount (ANOMALY_001)
-- CURRENCY_MISMATCH:          USD amount with INR currency label (ANOMALY_002)
-- NEGATIVE_AMOUNT:            Negative value — treated as refund (ANOMALY_003)
-- POST_EXIT_EXPENSE:          Expense after member's left_at date (ANOMALY_004)
-- SETTLEMENT_AS_EXPENSE:      Settlement logged as regular expense (ANOMALY_005)
-- MISSING_FIELDS:             Null/empty required field (ANOMALY_006)
-- UNKNOWN_MEMBER:             CSV name doesn't match any user (ANOMALY_007)
-- CONFLICTING_DUPLICATE:      Same event, different amounts (ANOMALY_008)
-- INVALID_DATE:               Unparseable date string (ANOMALY_009)
-- SPLIT_PERCENTAGE_MISMATCH:  Percentages don't sum to 100 (ANOMALY_010)
-- UNSUPPORTED_SPLIT_TYPE:     Split type not in SplitType enum (ANOMALY_011)
-- ZERO_AMOUNT:                Zero-value expense (ANOMALY_012)
CREATE TYPE anomaly_type AS ENUM (
    'DUPLICATE_EXPENSE',
    'CURRENCY_MISMATCH',
    'NEGATIVE_AMOUNT',
    'POST_EXIT_EXPENSE',
    'SETTLEMENT_AS_EXPENSE',
    'MISSING_FIELDS',
    'UNKNOWN_MEMBER',
    'CONFLICTING_DUPLICATE',
    'INVALID_DATE',
    'SPLIT_PERCENTAGE_MISMATCH',
    'UNSUPPORTED_SPLIT_TYPE',
    'ZERO_AMOUNT'
);

-- ENUM: User's decision on each flagged anomaly.
-- PENDING:  not yet reviewed (default)
-- APPROVED: user says "import this row anyway"
-- REJECTED: user says "skip this row"
CREATE TYPE user_decision AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

-- ==============================================================
-- IMPORT SESSIONS
-- ==============================================================
-- Each CSV upload creates exactly one import_session row.
-- This table tracks:
--   1. Who uploaded the file and when
--   2. How many rows were processed, imported, skipped
--   3. The current status of the import
--
-- The import lifecycle:
--   1. User uploads CSV → status = PENDING
--   2. System parses and detects anomalies → status = REVIEWING
--   3. User reviews and approves/rejects each anomaly
--   4. User clicks "Confirm Import"
--      → All approved rows written to DB in a single transaction
--      → status = COMPLETE (or FAILED if write fails)
--
-- This supports Step 3 (surface to user), Step 4 (approval flow),
-- and Step 6 (import report) of the CSV import pipeline.
-- ==============================================================

CREATE TABLE import_sessions (

    -- Primary key
    id              BIGSERIAL       PRIMARY KEY,

    -- The user who uploaded the CSV file.
    uploaded_by     BIGINT          NOT NULL,

    -- Original filename of the uploaded CSV.
    -- Stored for audit trail and display in the import report.
    filename        VARCHAR(500)    NOT NULL,

    -- When the file was uploaded.
    uploaded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Total number of data rows in the CSV (excluding header).
    total_rows      INTEGER         NOT NULL DEFAULT 0,

    -- Number of rows successfully imported into expenses/payments tables.
    imported_rows   INTEGER         NOT NULL DEFAULT 0,

    -- Number of rows skipped (rejected by user or auto-skipped for errors).
    skipped_rows    INTEGER         NOT NULL DEFAULT 0,

    -- Total number of anomalies detected across all rows.
    -- One row can have multiple anomalies (e.g., both duplicate AND currency mismatch).
    anomaly_count   INTEGER         NOT NULL DEFAULT 0,

    -- Current lifecycle status of this import session.
    status          import_status   NOT NULL DEFAULT 'PENDING',

    -- Foreign key
    CONSTRAINT fk_import_session_user
        FOREIGN KEY (uploaded_by) REFERENCES users (id),

    -- Validation: counts should be non-negative
    CONSTRAINT chk_import_counts_non_negative
        CHECK (total_rows >= 0 AND imported_rows >= 0 AND skipped_rows >= 0 AND anomaly_count >= 0),

    -- Validation: imported + skipped should not exceed total
    CONSTRAINT chk_import_counts_consistent
        CHECK (imported_rows + skipped_rows <= total_rows)
);

-- ==============================================================
-- IMPORT ANOMALIES
-- ==============================================================
-- Each row represents one detected anomaly in one CSV row.
-- A single CSV row can generate MULTIPLE anomaly records
-- (e.g., row has both currency mismatch AND is a duplicate).
--
-- This is the table that powers:
--   - The anomaly review UI (Step 3: AnomalyReview.jsx)
--   - Meera's approval flow (Step 4)
--   - The import report (Step 6: ImportReport.jsx)
--
-- Data flow:
--   1. AnomalyDetector creates ImportAnomaly objects in memory
--   2. These are returned to frontend for review (NOT yet in DB)
--   3. User approves/rejects each anomaly
--   4. On "Confirm Import", anomalies are persisted to this table
--      with the user's decision recorded
-- ==============================================================

CREATE TABLE import_anomalies (

    -- Primary key
    id                  BIGSERIAL       PRIMARY KEY,

    -- Which import session this anomaly belongs to.
    -- ON DELETE CASCADE: if an import session is deleted,
    -- all its anomalies are removed too.
    session_id          BIGINT          NOT NULL,

    -- CSV row number (1-indexed, excluding header).
    -- Used to help the user locate the problem in their spreadsheet.
    row_number          INTEGER         NOT NULL,

    -- The original, unmodified CSV line exactly as it appeared
    -- in the uploaded file. Stored for:
    --   1. Display in the anomaly review UI
    --   2. Audit trail (prove what the original data looked like)
    --   3. Debugging (reproduce parsing issues)
    raw_csv_row         TEXT            NOT NULL,

    -- Which of the 12 anomaly types was detected.
    -- Maps to ANOMALY_001 through ANOMALY_012 in the spec.
    anomaly_type        anomaly_type    NOT NULL,

    -- Human-readable description of what's wrong with this row.
    -- Example: "Duplicate: expense 'Groceries' on 2024-03-15 for 
    --           ₹2,400 already exists in the system."
    description         TEXT            NOT NULL,

    -- What the system recommends the user do about this anomaly.
    -- Example: "Auto-convert 50.00 USD to ₹4,150.00 INR using rate 83.00"
    -- Can be NULL for anomalies where no automatic fix is possible.
    suggested_action    TEXT,

    -- The user's decision on this specific anomaly.
    -- PENDING = not yet reviewed
    -- APPROVED = user says "import this row"
    -- REJECTED = user says "skip this row"
    -- Meera's requirement: she must approve every change.
    user_decision       user_decision   NOT NULL DEFAULT 'PENDING',

    -- When the user made their decision.
    -- NULL while user_decision = PENDING.
    decided_at          TIMESTAMPTZ,

    -- Foreign key
    CONSTRAINT fk_anomaly_session
        FOREIGN KEY (session_id) REFERENCES import_sessions (id)
        ON DELETE CASCADE
);

-- Index for "get all anomalies in a session" (import report view)
CREATE INDEX idx_anomaly_session ON import_anomalies (session_id);

-- Partial index for "get pending anomalies in a session"
-- (the approval workflow only shows unreviewed anomalies).
-- Partial indexes are smaller and faster because they only index
-- rows matching the WHERE clause.
CREATE INDEX idx_anomaly_pending
    ON import_anomalies (session_id)
    WHERE user_decision = 'PENDING';

-- ==============================================================
-- DEFERRED FOREIGN KEY: expenses.import_session_id → import_sessions
-- ==============================================================
-- This FK couldn't be created in V3 because import_sessions didn't
-- exist yet. Flyway runs migrations in version order (V1, V2, V3...),
-- so we add this FK here in V5 after both tables exist.
--
-- This column links imported expenses back to their source CSV session
-- for traceability. NULL for manually-created expenses.
-- ==============================================================

ALTER TABLE expenses
    ADD CONSTRAINT fk_expense_import_session
    FOREIGN KEY (import_session_id) REFERENCES import_sessions (id);

-- Index for "get all expenses from a specific import session"
-- (used in import report to show what was imported)
CREATE INDEX idx_expense_import_session
    ON expenses (import_session_id)
    WHERE import_session_id IS NOT NULL;

-- ==============================================================
-- COMMENTS
-- ==============================================================
COMMENT ON TABLE import_sessions IS 'CSV upload tracking — one row per upload, tracks lifecycle from PENDING to COMPLETE/FAILED';
COMMENT ON TABLE import_anomalies IS 'Detected data quality issues in CSV rows — each must be reviewed by user before import proceeds';
COMMENT ON COLUMN import_anomalies.raw_csv_row IS 'Original CSV line preserved verbatim for audit trail and debugging';
COMMENT ON COLUMN import_anomalies.user_decision IS 'Meera requirement: every flagged change must be explicitly approved or rejected';
COMMENT ON COLUMN import_anomalies.suggested_action IS 'System recommendation — user can override by choosing APPROVED or REJECTED';
