-- ==============================================================
-- V2: CREATE EXPENSE GROUPS AND GROUP MEMBERSHIPS
-- ==============================================================
-- Two tables are created here:
--
-- 1. 'expense_groups' — represents a shared expense group
--    (e.g., "Flat Expenses", "Goa Trip").
--    NOTE: We use 'expense_groups' instead of 'groups' because
--    GROUP is a reserved keyword in PostgreSQL. Using 'groups'
--    would require quoting ("groups") throughout the codebase,
--    which is error-prone and hurts readability.
--    See DECISIONS.md for full rationale.
--
-- 2. 'group_memberships' — tracks WHO belongs to WHICH group
--    and WHEN. This is the critical table for:
--    - Sam's requirement: "March expenses shouldn't affect my balance"
--    - Meera's requirement: post-exit expenses excluded
--
-- Design principle: TIME-RANGED MEMBERSHIPS
--    Instead of a simple boolean 'is_active' flag, we store
--    'joined_at' and 'left_at' dates. This allows the balance
--    calculation engine to determine which members were active
--    on any given expense date.
--
--    Rule: left_at IS NULL means the member is currently active.
-- ==============================================================

CREATE TABLE expense_groups (

    -- Primary key: auto-incrementing BIGINT
    id              BIGSERIAL       PRIMARY KEY,

    -- Group name: displayed in the UI group list.
    -- Examples: "Flat Expenses Feb-May", "Goa Trip"
    name            VARCHAR(255)    NOT NULL,

    -- Optional description for additional context
    description     TEXT,

    -- The user who created this group. FK to users(id).
    -- This does NOT imply ownership — all members have equal rights.
    -- It's tracked for audit purposes only.
    created_by      BIGINT          NOT NULL,

    -- When the group was created
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Foreign key: creator must be a valid user
    CONSTRAINT fk_groups_created_by
        FOREIGN KEY (created_by) REFERENCES users (id)
);

-- Index on creator for "my groups" queries
CREATE INDEX idx_groups_created_by ON expense_groups (created_by);

-- ==============================================================
-- GROUP MEMBERSHIPS (Time-Ranged)
-- ==============================================================
-- This table implements the core business rule for membership-
-- based expense filtering. Each row represents a membership
-- period: [joined_at, left_at].
--
-- Examples from the assignment:
--   - Aisha, Rohan, Priya: joined Feb 1, left_at = NULL (still active)
--   - Meera: joined Feb 1, left_at = March 31 (moved out end of March)
--   - Sam: joined April 15, left_at = NULL (moved in mid-April)
--   - Dev: joined for trip period only, left_at = trip end date
--
-- Balance calculation rule (enforced in BalanceCalculationService):
--   For each expense on date D, include member M only if:
--     M.joined_at <= D AND (M.left_at IS NULL OR M.left_at >= D)
-- ==============================================================

CREATE TABLE group_memberships (

    -- Primary key
    id              BIGSERIAL       PRIMARY KEY,

    -- Which group this membership belongs to
    group_id        BIGINT          NOT NULL,

    -- Which user is the member
    user_id         BIGINT          NOT NULL,

    -- Date the member joined the group.
    -- We use DATE (not TIMESTAMP) because membership granularity
    -- is per-day — the assignment says "mid-April" and "end of March",
    -- not specific hours. DATE simplifies comparison logic in the
    -- balance calculation engine.
    joined_at       DATE            NOT NULL,

    -- Date the member left the group.
    -- NULL means the member is currently active.
    -- RULE: Any expense with expense_date > left_at will NOT include
    -- this member in its split calculation.
    left_at         DATE,

    -- Foreign keys
    CONSTRAINT fk_membership_group
        FOREIGN KEY (group_id) REFERENCES expense_groups (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    -- Unique constraint: a user can only have one active membership
    -- period starting on a given date in a given group.
    -- This prevents duplicate memberships but allows a user to
    -- leave and re-join (different joined_at dates).
    CONSTRAINT uq_membership_group_user_joined
        UNIQUE (group_id, user_id, joined_at),

    -- Validation: if both dates are present, left_at must be
    -- on or after joined_at (can't leave before joining)
    CONSTRAINT chk_membership_dates
        CHECK (left_at IS NULL OR left_at >= joined_at)
);

-- Index for "get all members of a group" queries (used frequently)
CREATE INDEX idx_membership_group ON group_memberships (group_id);

-- Index for "get all groups a user belongs to" queries
CREATE INDEX idx_membership_user ON group_memberships (user_id);

-- Partial index for fast lookup of currently active members.
-- This is heavily used by the balance calculation engine to
-- determine eligible members for expense splits.
-- PostgreSQL partial indexes only index rows matching the WHERE clause,
-- making them smaller and faster than full indexes.
CREATE INDEX idx_membership_active
    ON group_memberships (group_id)
    WHERE left_at IS NULL;

-- ==============================================================
-- COMMENTS
-- ==============================================================
COMMENT ON TABLE expense_groups IS 'Shared expense groups — named "expense_groups" to avoid PostgreSQL reserved word "groups"';
COMMENT ON TABLE group_memberships IS 'Time-ranged group memberships: joined_at/left_at determine which expenses affect each member';
COMMENT ON COLUMN group_memberships.left_at IS 'NULL = currently active member. Expenses after this date exclude this member from splits.';
COMMENT ON COLUMN group_memberships.joined_at IS 'Membership start date. Expenses before this date exclude this member from splits.';
