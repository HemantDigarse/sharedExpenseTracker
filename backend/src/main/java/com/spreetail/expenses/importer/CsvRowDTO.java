package com.spreetail.expenses.importer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO representing a single parsed CSV row before anomaly detection.
 *
 * <p>This is the intermediate representation between raw CSV text
 * and the final Expense/Payment entity. Fields are strings initially
 * (from CSV) and parsed/validated during anomaly detection.
 *
 * <p>Expected CSV columns: date, description, paid_by, amount,
 * currency, split_type, [split-specific fields]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvRowDTO {

    // ---- Raw string values from CSV ----
    private String date;
    private String description;
    private String paidBy;
    private String amount;
    private String currency;
    private String splitType;

    // ---- Parsed values (set during validation) ----
    /** Parsed date — set by AnomalyDetector.checkInvalidDate() */
    private LocalDate parsedDate;

    /** Parsed amount — set during CSV parsing */
    private BigDecimal parsedAmount;

    // ---- Split-specific fields (populated based on split type) ----
    /** For PERCENTAGE splits: member name → percentage */
    private Map<String, BigDecimal> splitPercentages;

    /** For EXACT splits: member name → amount */
    private Map<String, BigDecimal> splitExactAmounts;

    /** For SHARES splits: member name → number of shares */
    private Map<String, Integer> splitShares;

    /** The original raw CSV line (preserved for audit trail) */
    private String rawLine;

    /** Row number in the CSV file (1-indexed, excluding header) */
    private int rowNumber;
}
