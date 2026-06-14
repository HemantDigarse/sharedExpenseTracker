-- ==============================================================
-- V3: CREATE EXPENSES AND EXPENSE SPLITS
-- ==============================================================
-- Two tables are created here:
--
-- 1. 'expenses' — each row is a single shared expense
--    (e.g., "Groceries ₹2,400 paid by Rohan on March 15").
--
-- 2. 'expense_splits' — each row is one person's share of an expense.
--    For an expense split equally among 4 people, there will be
--    4 rows in expense_splits linked to 1 row in expenses.
--
-- Key design decisions:
--   - NUMERIC(15,2) for all monetary values (maps to Java BigDecimal).
--     NEVER use FLOAT or DOUBLE for money — they cause rounding errors.
--     Example: 0.1 + 0.2 = 0.30000000000000004 in floating point.
--
--   - 'amount_in_inr' is always populated at creation/import time.
--     This means the balance calculation engine NEVER needs to do
--     currency conversion — it always works in INR.
--     See Priya's requirement: "Fix the USD/INR currency mismatch."
--
--   - 'is_settlement' flag: during CSV import, if the AnomalyDetector
--     identifies a row as a settlement (ANOMALY_005), it's imported
--     into the 'payments' table instead. But we keep this flag on
--     expenses for any edge cases where a settlement needs to be
--     recorded in both places.
--
--   - PostgreSQL ENUMs for 'split_type' and 'currency' enforce
--     valid values at the database level, not just application level.
-- ==============================================================

-- ENUM: Supported currencies.
-- Currently INR and USD per the assignment.
-- Adding a new currency requires a Flyway migration to ALTER TYPE.
CREATE TYPE currency AS ENUM ('INR', 'USD');

-- ENUM: How an expense is divided among group members.
-- EQUAL:      amount ÷ number of eligible members
-- EXACT:      each member's share is specified explicitly
-- PERCENTAGE: each member pays a percentage (must sum to 100%)
-- SHARES:     each member has N shares; amount × (my_shares ÷ total_shares)
CREATE TYPE split_type AS ENUM ('EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES');

CREATE TABLE expenses (

    -- Primary key
    id                  BIGSERIAL       PRIMARY KEY,

    -- Which group this expense belongs to.
    -- All participants must be members of this group on the expense_date.
    group_id            BIGINT          NOT NULL,

    -- Who paid for this expense (the payer).
    -- This person is owed money by the other participants.
    paid_by             BIGINT          NOT NULL,

    -- Human-readable description of what the expense was for.
    -- Examples: "Groceries", "Electricity bill March", "Goa hotel"
    -- Used by AnomalyDetector for duplicate detection (ANOMALY_001)
    -- and settlement detection (ANOMALY_005).
    description         VARCHAR(500)    NOT NULL,

    -- Original amount in the original currency.
    -- Example: if someone paid $50 USD, this is 50.00.
    -- NUMERIC(15,2) maps to Java BigDecimal — NEVER use Double/Float.
    amount              NUMERIC(15,2)   NOT NULL,

    -- Original currency of the expense.
    -- Used to determine if conversion is needed.
    currency            currency        NOT NULL DEFAULT 'INR',

    -- Pre-converted amount in INR (Indian Rupees).
    -- ALWAYS populated, regardless of original currency.
    -- If currency = INR, then amount_in_inr = amount.
    -- If currency = USD, then amount_in_inr = amount × USD_TO_INR_RATE.
    -- This is the ONLY column used by BalanceCalculationService.
    -- Conversion happens once at import/creation time, not on every query.
    amount_in_inr       NUMERIC(15,2)   NOT NULL,

    -- How this expense should be divided among participants.
    -- Determines which columns in expense_splits are used.
    split_type          split_type      NOT NULL DEFAULT 'EQUAL',

    -- Date the expense was incurred (NOT when it was entered).
    -- Critical for membership filtering: only members active on
    -- this date are included in the split.
    -- DATE type (not TIMESTAMP) because expenses happen on a day,
    -- not at a specific second.
    expense_date        DATE            NOT NULL,

    -- When this expense record was created in the system.
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Flag: true if this expense is actually a settlement/payment.
    -- During CSV import, ANOMALY_005 detects settlements logged
    -- as expenses and routes them to the payments table instead.
    -- This flag is a safety net for manual entry edge cases.
    is_settlement       BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Link to the CSV import session that created this expense.
    -- NULL for manually-created expenses.
    -- FK is added in V5 (after import_sessions table exists)
    -- to avoid circular dependency in Flyway migration order.
    import_session_id   BIGINT,

    -- Foreign keys
    CONSTRAINT fk_expense_group
        FOREIGN KEY (group_id) REFERENCES expense_groups (id),

    CONSTRAINT fk_expense_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id),

    -- Validation: amount_in_inr must be consistent with currency.
    -- We don't enforce exact conversion rate here (that's application logic),
    -- but we ensure both amounts have the same sign.
    CONSTRAINT chk_expense_amount_sign
        CHECK (
            (amount >= 0 AND amount_in_inr >= 0) OR
            (amount < 0 AND amount_in_inr < 0)
        )
);

-- Index for "list all expenses in a group" (most common query)
CREATE INDEX idx_expense_group ON expenses (group_id);

-- Index for "list expenses by date range" (filtered views, reports)
CREATE INDEX idx_expense_date ON expenses (expense_date);

-- Index for "list expenses paid by a user" (balance calculation)
CREATE INDEX idx_expense_paid_by ON expenses (paid_by);

-- Composite index for duplicate detection (ANOMALY_001):
-- Same group + description + date + amount = likely duplicate
CREATE INDEX idx_expense_duplicate_check
    ON expenses (group_id, description, expense_date, amount);

-- ==============================================================
-- EXPENSE SPLITS
-- ==============================================================
-- Each row represents ONE person's share of ONE expense.
-- The number of split rows per expense = number of eligible members.
--
-- Depending on split_type, different columns are populated:
--   EQUAL:      only final_amount_owed (calculated: amount ÷ count)
--   EXACT:      share_amount + final_amount_owed (same value)
--   PERCENTAGE: share_percentage + final_amount_owed (calculated)
--   SHARES:     share_units + final_amount_owed (calculated)
--
-- 'final_amount_owed' is ALWAYS populated and ALWAYS in INR.
-- It is the single source of truth for balance calculations.
-- This supports Rohan's requirement: every balance figure traces
-- back to individual expense_split rows.
-- ==============================================================

CREATE TABLE expense_splits (

    -- Primary key
    id                  BIGSERIAL       PRIMARY KEY,

    -- Which expense this split belongs to.
    -- ON DELETE CASCADE: if an expense is deleted, all its splits
    -- are automatically removed. This maintains referential integrity
    -- without requiring application-level cleanup.
    expense_id          BIGINT          NOT NULL,

    -- Which user owes this share of the expense.
    user_id             BIGINT          NOT NULL,

    -- Used for EXACT split type: the explicit amount this person owes.
    -- NULL for other split types.
    -- Example: "Rohan pays exactly ₹500 for his portion"
    share_amount        NUMERIC(15,2),

    -- Used for PERCENTAGE split type: what percentage this person pays.
    -- NULL for other split types. All percentages for an expense
    -- must sum to exactly 100.00 (validated in application layer).
    -- NUMERIC(5,2) allows values like 33.33, 25.00, etc.
    share_percentage    NUMERIC(5,2),

    -- Used for SHARES split type: number of shares this person has.
    -- NULL for other split types.
    -- Example: If total shares = 10 and this person has 3 shares,
    -- they owe 30% of the expense.
    share_units         INTEGER,

    -- THE MOST IMPORTANT COLUMN IN THIS TABLE.
    -- Always populated. Always in INR. Always calculated.
    -- This is what the balance engine sums up.
    -- Rounding: BigDecimal HALF_UP to 2 decimal places (see BalanceCalculationService).
    final_amount_owed   NUMERIC(15,2)   NOT NULL,

    -- Foreign keys
    CONSTRAINT fk_split_expense
        FOREIGN KEY (expense_id) REFERENCES expenses (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_split_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    -- Each user can only have one split record per expense.
    -- Prevents accidental double-counting.
    CONSTRAINT uq_split_expense_user
        UNIQUE (expense_id, user_id)
);

-- Index for "get all splits for an expense" (expense detail view)
CREATE INDEX idx_split_expense ON expense_splits (expense_id);

-- Index for "get all splits involving a user" (balance calculation).
-- This is the most performance-critical index in the system —
-- BalanceCalculationService queries all splits for a user across
-- all expenses in a group to compute their net balance.
CREATE INDEX idx_split_user ON expense_splits (user_id);

-- ==============================================================
-- COMMENTS
-- ==============================================================
COMMENT ON TABLE expenses IS 'Shared expenses within a group — amount_in_inr is always populated for consistent balance calculations';
COMMENT ON TABLE expense_splits IS 'Individual share of each expense per user — final_amount_owed is the source of truth for balances';
COMMENT ON COLUMN expenses.amount_in_inr IS 'Pre-converted INR amount — the ONLY column used by BalanceCalculationService';
COMMENT ON COLUMN expenses.is_settlement IS 'Safety flag — true settlements should be in the payments table';
COMMENT ON COLUMN expense_splits.final_amount_owed IS 'Always in INR, always populated — the single source of truth for balance calculations';
