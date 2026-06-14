-- ==============================================================
-- V6: SEED EXCHANGE RATES (Static USD→INR Rate)
-- ==============================================================
-- DECISION: Static rate vs. API-based dynamic rate.
--
-- We use a STATIC rate of 1 USD = 83.00 INR for this project.
--
-- RATIONALE (documented in DECISIONS.md):
--   1. The expense data is historical (Feb–May timeframe).
--      Retroactive reconciliation requires consistent rates,
--      not rates that change daily.
--   2. The assignment specifically says "a dollar is not a rupee"
--      (Priya's requirement) — the key fix is applying ANY
--      reasonable USD→INR conversion, not getting the exact
--      live market rate.
--   3. A static rate eliminates external API dependencies,
--      making the app work offline and in air-gapped environments.
--   4. The rate is stored in the DB (not hardcoded) so it can
--      be updated via a new migration or admin API without
--      redeploying the application.
--
-- The effective_date is set to 2024-01-01 (before any expenses)
-- so this rate applies to ALL historical expenses. If different
-- rates are needed for different dates, additional rows can be
-- inserted with later effective_dates.
--
-- Source is 'STATIC' for audit trail clarity.
-- ==============================================================

INSERT INTO exchange_rates (from_currency, to_currency, rate, effective_date, source)
VALUES ('USD', 'INR', 83.000000, '2024-01-01', 'STATIC');

-- NOTE: To support INR→USD conversion (reverse), uncomment:
-- INSERT INTO exchange_rates (from_currency, to_currency, rate, effective_date, source)
-- VALUES ('INR', 'USD', 0.012048, '2024-01-01', 'STATIC');
-- Rate: 1/83 = 0.012048 (rounded to 6 decimal places)
