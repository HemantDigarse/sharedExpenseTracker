package com.spreetail.expenses.importer;

/**
 * User's decision on a flagged anomaly.
 * Maps to PostgreSQL ENUM {@code user_decision} in V5.
 */
public enum UserDecision {
    PENDING,   // Not yet reviewed
    APPROVED,  // User says "import this row"
    REJECTED   // User says "skip this row"
}
