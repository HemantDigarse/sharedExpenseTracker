-- ==============================================================
-- V4: CREATE PAYMENTS (SETTLEMENTS) AND EXCHANGE RATES
-- ==============================================================
-- Two tables are created here:
--
-- 1. 'payments' — records actual money transfers between users
--    to settle outstanding balances. These are NOT expenses —
--    they REDUCE balances rather than creating new ones.
--    Example: "Rohan pays Aisha ₹1,200 to settle up."
--
--    RULE (enforced in BalanceCalculationService):
--    Payments are applied AFTER calculating expense-based balances.
--    Net balance = (what you owe from expenses) - (what you've paid)
--
-- 2. 'exchange_rates' — stores currency conversion rates.
--    DECISION: We use a static rate (1 USD = 83.00 INR) seeded
--    in V6. The rate is stored in the DB rather than hardcoded
--    so it can be updated without redeploying the application.
--    See DECISIONS.md: "Static vs dynamic currency rate."
-- ==============================================================

CREATE TABLE payments (

    -- Primary key
    id              BIGSERIAL       PRIMARY KEY,

    -- Which group this payment belongs to.
    -- Payments only make sense within the context of a group's
    -- shared expenses.
    group_id        BIGINT          NOT NULL,

    -- The user making the payment (the one who owes money).
    paid_by         BIGINT          NOT NULL,

    -- The user receiving the payment (the one who is owed money).
    paid_to         BIGINT          NOT NULL,

    -- Payment amount in INR.
    -- All payments are in INR because all balance calculations
    -- are performed in INR (see expenses.amount_in_inr).
    -- NUMERIC(15,2) maps to Java BigDecimal.
    amount          NUMERIC(15,2)   NOT NULL,

    -- Date the payment was made.
    -- Used for audit trail and reporting, but does NOT affect
    -- balance calculations (unlike expense_date, which determines
    -- membership eligibility).
    payment_date    DATE            NOT NULL,

    -- Optional notes for context.
    -- Example: "Settling March expenses", "Venmo transfer"
    notes           TEXT,

    -- When this payment record was created in the system.
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Foreign keys
    CONSTRAINT fk_payment_group
        FOREIGN KEY (group_id) REFERENCES expense_groups (id),

    CONSTRAINT fk_payment_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id),

    CONSTRAINT fk_payment_paid_to
        FOREIGN KEY (paid_to) REFERENCES users (id),

    -- Validation: a person cannot pay themselves.
    -- This would be a data entry error — catch it at DB level
    -- as a safety net (also validated in application layer).
    CONSTRAINT chk_payment_not_self
        CHECK (paid_by <> paid_to),

    -- Validation: payment amount must be positive.
    -- Negative payments don't make sense — if person A overpaid
    -- person B, that's a separate payment in the opposite direction.
    CONSTRAINT chk_payment_positive
        CHECK (amount > 0)
);

-- Index for "list all payments in a group" (group detail view)
CREATE INDEX idx_payment_group ON payments (group_id);

-- Index for "payments made by a user" (balance calculation: deductions)
CREATE INDEX idx_payment_paid_by ON payments (paid_by);

-- Index for "payments received by a user" (balance calculation: credits)
CREATE INDEX idx_payment_paid_to ON payments (paid_to);

-- ==============================================================
-- EXCHANGE RATES
-- ==============================================================
-- Stores currency conversion rates with effective dates.
-- This table is used by CurrencyConversionService to convert
-- USD expenses to INR at import/creation time.
--
-- DESIGN: Rates are stored per (from_currency, to_currency, date)
-- tuple. This allows:
--   1. Historical rate lookups (what was the rate on March 15?)
--   2. Future rate updates without breaking existing data
--   3. Audit trail of which rate was used
--
-- For this project, we seed a single static rate in V6.
-- The 'source' column tracks whether the rate came from:
--   - 'STATIC': hardcoded default rate
--   - 'EXCHANGERATE_API': fetched from ExchangeRate-API
--   - 'MANUAL': entered by an admin
-- ==============================================================

CREATE TABLE exchange_rates (

    -- Primary key
    id              BIGSERIAL       PRIMARY KEY,

    -- Source currency (e.g., USD)
    from_currency   currency        NOT NULL,

    -- Target currency (e.g., INR)
    to_currency     currency        NOT NULL,

    -- Conversion rate: 1 unit of from_currency = rate units of to_currency.
    -- Example: from=USD, to=INR, rate=83.000000 means 1 USD = ₹83.
    -- NUMERIC(12,6) provides 6 decimal places for FX rate precision.
    -- Most exchange APIs provide 4-6 decimal places.
    rate            NUMERIC(12,6)   NOT NULL,

    -- Date this rate is effective for.
    -- When converting an expense, the system looks up the rate
    -- with the closest effective_date <= expense_date.
    effective_date  DATE            NOT NULL,

    -- Where this rate came from (audit trail).
    source          VARCHAR(100)    NOT NULL DEFAULT 'STATIC',

    -- Unique constraint: only one rate per currency pair per date.
    -- Prevents conflicting rates for the same day.
    CONSTRAINT uq_exchange_rate_pair_date
        UNIQUE (from_currency, to_currency, effective_date),

    -- Validation: rate must be positive.
    CONSTRAINT chk_rate_positive
        CHECK (rate > 0),

    -- Validation: can't convert a currency to itself.
    CONSTRAINT chk_rate_different_currencies
        CHECK (from_currency <> to_currency)
);

-- Index for rate lookups: find rate for a currency pair on/before a date.
-- The most common query pattern is:
-- SELECT rate FROM exchange_rates
-- WHERE from_currency = 'USD' AND to_currency = 'INR'
--   AND effective_date <= :expense_date
-- ORDER BY effective_date DESC LIMIT 1;
CREATE INDEX idx_exchange_rate_lookup
    ON exchange_rates (from_currency, to_currency, effective_date DESC);

-- ==============================================================
-- COMMENTS
-- ==============================================================
COMMENT ON TABLE payments IS 'Settlement payments between users — reduce balances, not create new expenses';
COMMENT ON TABLE exchange_rates IS 'Currency conversion rates with effective dates — used to convert USD expenses to INR at import time';
COMMENT ON COLUMN payments.amount IS 'Payment amount in INR — all balance calculations use INR';
COMMENT ON COLUMN exchange_rates.rate IS '1 unit of from_currency = rate units of to_currency (e.g., 83.0 for USD→INR)';
COMMENT ON COLUMN exchange_rates.source IS 'Rate origin: STATIC, EXCHANGERATE_API, or MANUAL — for audit trail';
