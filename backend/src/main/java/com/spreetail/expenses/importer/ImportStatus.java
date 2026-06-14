package com.spreetail.expenses.importer;

/**
 * Lifecycle states for a CSV import session.
 * Maps to PostgreSQL ENUM {@code import_status} in V5.
 */
public enum ImportStatus {
    PENDING,    // File uploaded, not yet parsed
    REVIEWING,  // Anomalies detected, awaiting user approval
    COMPLETE,   // Import finished successfully
    FAILED      // Import failed (parse error, DB write error, etc.)
}
