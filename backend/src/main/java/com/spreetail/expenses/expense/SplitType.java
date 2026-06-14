package com.spreetail.expenses.expense;

/**
 * Enum representing how an expense is divided among group members.
 *
 * <p>Maps to the PostgreSQL ENUM type 'split_type' created in
 * Flyway migration V3.
 *
 * <p>Each split type determines which columns in the
 * {@code expense_splits} table are populated:
 *
 * <ul>
 *   <li>{@link #EQUAL} — {@code final_amount_owed} only (auto-calculated)</li>
 *   <li>{@link #EXACT} — {@code share_amount} + {@code final_amount_owed}</li>
 *   <li>{@link #PERCENTAGE} — {@code share_percentage} + {@code final_amount_owed}</li>
 *   <li>{@link #SHARES} — {@code share_units} + {@code final_amount_owed}</li>
 * </ul>
 *
 * <p>In ALL cases, {@code final_amount_owed} is populated and is
 * the single source of truth for balance calculations.
 */
public enum SplitType {

    /**
     * EQUAL split: expense amount ÷ number of eligible members.
     *
     * <p>Example: ₹2,400 split among 4 people = ₹600.00 each.
     *
     * <p>"Eligible members" means members who were active in the group
     * on the expense_date (checked via group_memberships.joined_at/left_at).
     */
    EQUAL,

    /**
     * EXACT split: each member's share is specified explicitly.
     *
     * <p>Example: Dinner ₹3,000 — Aisha owes ₹1,000, Rohan owes ₹2,000.
     *
     * <p>Validation: all exact amounts must sum to the total expense amount.
     * If they don't, a {@link com.spreetail.expenses.common.BusinessRuleException}
     * is thrown.
     */
    EXACT,

    /**
     * PERCENTAGE split: each member pays a percentage of the total.
     *
     * <p>Example: Rent ₹20,000 — Aisha 30%, Rohan 30%, Priya 40%.
     *
     * <p>Validation: all percentages must sum to exactly 100%.
     * If they don't, this triggers ANOMALY_010 during CSV import
     * or a BusinessRuleException during manual entry.
     */
    PERCENTAGE,

    /**
     * SHARES split: each member has a number of shares.
     * Their owed amount = (their shares ÷ total shares) × expense amount.
     *
     * <p>Example: Trip expense ₹10,000 with shares:
     *   Aisha=2, Rohan=3, Priya=1 (total=6).
     *   Aisha owes: (2/6) × 10,000 = ₹3,333.33
     *
     * <p>Useful for unequal splits without specifying exact amounts
     * or percentages.
     */
    SHARES
}
