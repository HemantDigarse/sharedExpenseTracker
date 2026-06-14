package com.spreetail.expenses.importer;

/**
 * Enum of all detectable anomaly types in the CSV import pipeline.
 *
 * <p>Each type maps to a specific detection rule in {@link AnomalyDetector}.
 * Maps to the PostgreSQL ENUM {@code anomaly_type} in migration V5.
 *
 * <p>Naming convention: matches the DB ENUM values exactly.
 */
public enum AnomalyType {

    /**
     * ANOMALY_001: Same description + date + amount already exists.
     * Policy: Flag for user approval (Meera's requirement).
     * Do NOT auto-delete.
     */
    DUPLICATE_EXPENSE("Duplicate expense detected"),

    /**
     * ANOMALY_002: Amount looks like USD but currency says INR.
     * Policy: Convert using documented rate, flag row.
     */
    CURRENCY_MISMATCH("Currency mismatch — possible USD treated as INR"),

    /**
     * ANOMALY_003: Negative amount in expense.
     * Policy: Treat as refund, create negative split, flag.
     */
    NEGATIVE_AMOUNT("Negative amount — treated as refund"),

    /**
     * ANOMALY_004: Expense after member's left_at date.
     * Policy: Exclude that member from split, flag row.
     * (Sam's and Meera's requirement)
     */
    POST_EXIT_EXPENSE("Post-exit expense — member left before this date"),

    /**
     * ANOMALY_005: Settlement logged as expense.
     * Policy: Import as Payment record, not Expense.
     */
    SETTLEMENT_AS_EXPENSE("Settlement logged as regular expense"),

    /**
     * ANOMALY_006: Missing required fields.
     * Policy: Skip row, log reason.
     */
    MISSING_FIELDS("Missing required fields"),

    /**
     * ANOMALY_007: Name in CSV doesn't match any user.
     * Policy: Pause import, ask user to map name.
     */
    UNKNOWN_MEMBER("Unknown member name — not found in system"),

    /**
     * ANOMALY_008: Two rows for same event with different amounts.
     * Policy: Flag both, ask user which wins.
     */
    CONFLICTING_DUPLICATE("Conflicting duplicate — same event, different amounts"),

    /**
     * ANOMALY_009: Unrecognizable date string.
     * Policy: Attempt common formats, flag if all fail.
     */
    INVALID_DATE("Invalid or unparseable date format"),

    /**
     * ANOMALY_010: Split percentages don't sum to 100.
     * Policy: Flag, do not import until resolved.
     */
    SPLIT_PERCENTAGE_MISMATCH("Split percentages do not sum to 100%"),

    /**
     * ANOMALY_011: Split type not in SplitType enum.
     * Policy: Skip row, log explanation.
     */
    UNSUPPORTED_SPLIT_TYPE("Unsupported split type"),

    /**
     * ANOMALY_012: Zero amount expense.
     * Policy: Skip row, log as data entry error.
     */
    ZERO_AMOUNT("Zero amount expense — likely data entry error");

    private final String description;

    AnomalyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
