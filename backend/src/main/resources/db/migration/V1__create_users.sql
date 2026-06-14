-- ==============================================================
-- V1: CREATE USERS TABLE
-- ==============================================================
-- The 'users' table stores authentication credentials and profile
-- information for all application users. Each user can belong to
-- multiple expense groups and participate in shared expenses.
--
-- Design decisions:
--   - 'email' is the login identifier (UNIQUE constraint)
--   - 'password_hash' stores BCrypt-encoded passwords (never plaintext)
--   - TIMESTAMPTZ (timestamp with time zone) is used instead of
--     TIMESTAMP to ensure correct behavior across time zones
--   - 'updated_at' tracks the last profile modification
-- ==============================================================

CREATE TABLE users (

    -- Primary key: auto-incrementing BIGINT.
    -- BIGSERIAL = BIGINT + auto-increment sequence.
    -- We use BIGINT (not INT) to avoid hitting the 2.1B row limit
    -- in production systems, even though this app won't need it.
    id              BIGSERIAL       PRIMARY KEY,

    -- Email address: used as the unique login identifier.
    -- VARCHAR(255) is the de facto standard max length for emails
    -- per RFC 5321 (max 254 chars for addr-spec).
    email           VARCHAR(255)    NOT NULL,

    -- BCrypt password hash: always 60 characters for BCrypt,
    -- but we use VARCHAR(255) for flexibility if we switch
    -- to a different hashing algorithm in the future.
    password_hash   VARCHAR(255)    NOT NULL,

    -- Display name: shown in expense splits, balance summaries,
    -- and settlement suggestions. Maps to flatmate names like
    -- "Aisha", "Rohan", "Priya", "Meera", "Sam", "Dev".
    full_name       VARCHAR(255)    NOT NULL,

    -- Audit timestamps: track when the account was created
    -- and last modified. DEFAULT NOW() populates automatically
    -- on INSERT; application code updates 'updated_at' on changes.
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Unique constraint on email: ensures no duplicate accounts.
-- Also serves as an index for fast login lookups.
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

-- Index on email for fast authentication queries.
-- (The UNIQUE constraint above implicitly creates an index,
--  but we name it explicitly for clarity in EXPLAIN plans.)
-- Note: PostgreSQL auto-creates an index for UNIQUE constraints,
-- so this explicit index is technically redundant. Kept for
-- documentation purposes — the UNIQUE constraint's implicit
-- index will be used.
CREATE INDEX idx_users_email ON users (email);

-- ==============================================================
-- COMMENTS ON TABLE AND COLUMNS
-- ==============================================================
COMMENT ON TABLE users IS 'Application users who can create groups, log expenses, and settle balances';
COMMENT ON COLUMN users.email IS 'Unique login identifier — RFC 5321 compliant email address';
COMMENT ON COLUMN users.password_hash IS 'BCrypt-encoded password hash — never store plaintext';
COMMENT ON COLUMN users.full_name IS 'Display name shown in expense splits and settlement summaries';
