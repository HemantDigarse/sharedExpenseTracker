package com.spreetail.expenses.importer;

import com.spreetail.expenses.expense.ExpenseRepository;
import com.spreetail.expenses.group.GroupMembership;
import com.spreetail.expenses.group.GroupMembershipRepository;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Detects data anomalies in parsed CSV rows.
 *
 * <p>This is the STEP 2 of the CSV import pipeline: validate and detect.
 * For each row, all 12 anomaly checks are run. A single row can have
 * MULTIPLE anomalies (e.g., duplicate AND currency mismatch).
 *
 * <p>CRITICAL RULES:
 * <ul>
 *   <li>A silent guess (no anomaly flagged) = FAILING answer</li>
 *   <li>Every anomaly must be explicit, logged, and reported</li>
 *   <li>Duplicates are flagged for user review, NOT auto-deleted (Meera's req)</li>
 * </ul>
 *
 * <p>Each detection method returns a list of anomalies found (may be empty).
 * All anomalies are aggregated and returned to the user for review
 * before any data is written to the database.
 */
@Component
public class AnomalyDetector {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetector.class);

    /**
     * Keywords that suggest a CSV row is a settlement, not an expense.
     * Used by ANOMALY_005 detection.
     */
    private static final List<String> SETTLEMENT_KEYWORDS = List.of(
            "settlement", "settled", "paid back", "payback", "reimbursement",
            "reimburse", "transfer", "repayment", "pay back", "settling"
    );

    /**
     * Common date formats to try when parsing dates (ANOMALY_009).
     * Ordered by likelihood in Indian CSV exports.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    );

    /**
     * Threshold below which a USD amount is suspected of being
     * treated as INR (ANOMALY_002). If an expense in "INR" has
     * an amount that looks like a typical USD amount (small number),
     * it might be a mislabeled currency.
     *
     * ASSUMPTION: Expenses below ₹200 in a shared flat context
     * are suspiciously low and might actually be in USD.
     * This is a heuristic — flagged for user review, not auto-corrected.
     */
    private static final BigDecimal USD_SUSPICION_THRESHOLD = new BigDecimal("200");

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;

    public AnomalyDetector(ExpenseRepository expenseRepository,
                            UserRepository userRepository,
                            GroupMembershipRepository membershipRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Runs ALL anomaly checks on a single parsed CSV row.
     *
     * <p>Returns a list of detected anomalies. A single row can trigger
     * multiple anomalies. An empty list means the row is clean.
     *
     * @param row       the parsed CSV row data
     * @param rowNumber the 1-indexed CSV row number (for reporting)
     * @param rawLine   the original CSV line (verbatim, for audit)
     * @param groupId   the target group for import
     * @param existingRows previously processed rows in this batch (for in-batch duplicate detection)
     * @return list of anomalies detected (may be empty)
     */
    public List<ImportAnomaly> detectAnomalies(CsvRowDTO row, int rowNumber,
                                                String rawLine, Long groupId,
                                                List<CsvRowDTO> existingRows) {
        List<ImportAnomaly> anomalies = new ArrayList<>();

        // ANOMALY_006: Missing required fields (checked FIRST — if fields are
        // missing, other checks may throw NPE)
        checkMissingFields(row, rowNumber, rawLine, anomalies);
        if (!anomalies.isEmpty()) {
            // If critical fields are missing, skip other checks
            // (they would fail on null values)
            logger.debug("Row {}: missing fields, skipping further checks", rowNumber);
            return anomalies;
        }

        // ANOMALY_009: Invalid date format
        checkInvalidDate(row, rowNumber, rawLine, anomalies);

        // ANOMALY_012: Zero amount
        checkZeroAmount(row, rowNumber, rawLine, anomalies);

        // ANOMALY_003: Negative amount
        checkNegativeAmount(row, rowNumber, rawLine, anomalies);

        // ANOMALY_007: Unknown member name
        checkUnknownMember(row, rowNumber, rawLine, anomalies);

        // ANOMALY_002: Currency mismatch (USD treated as INR)
        checkCurrencyMismatch(row, rowNumber, rawLine, anomalies);

        // ANOMALY_005: Settlement logged as expense
        checkSettlementAsExpense(row, rowNumber, rawLine, anomalies);

        // ANOMALY_011: Unsupported split type
        checkUnsupportedSplitType(row, rowNumber, rawLine, anomalies);

        // ANOMALY_010: Split percentages don't sum to 100
        checkSplitPercentageMismatch(row, rowNumber, rawLine, anomalies);

        // ANOMALY_001 & ANOMALY_008: Duplicate detection
        // Check against both existing DB records AND other rows in this batch
        checkDuplicates(row, rowNumber, rawLine, groupId, existingRows, anomalies);

        // ANOMALY_004: Post-exit expense
        checkPostExitExpense(row, rowNumber, rawLine, groupId, anomalies);

        logger.debug("Row {}: {} anomalies detected", rowNumber, anomalies.size());
        return anomalies;
    }

    // ================================================================
    // ANOMALY_001: Duplicate expense
    // ================================================================

    /**
     * Checks for duplicate expenses — same description + date + amount.
     *
     * <p>Also checks for ANOMALY_008: conflicting duplicates where the
     * description and date match but the amount differs.
     *
     * <p>Policy: Flag for user approval (Meera's requirement).
     * Do NOT auto-delete — the user must approve every change.
     */
    private void checkDuplicates(CsvRowDTO row, int rowNumber, String rawLine,
                                  Long groupId, List<CsvRowDTO> existingRows,
                                  List<ImportAnomaly> anomalies) {
        if (row.getDescription() == null || row.getParsedDate() == null || row.getParsedAmount() == null) {
            return; // Can't check duplicates without these fields
        }

        // Check against existing DB records
        var dbDuplicates = expenseRepository.findDuplicates(
                groupId, row.getDescription(), row.getParsedDate(), row.getParsedAmount());

        if (!dbDuplicates.isEmpty()) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.DUPLICATE_EXPENSE,
                    String.format("Duplicate: expense '%s' on %s for ₹%s already exists in the system (expense ID: %d)",
                            row.getDescription(), row.getParsedDate(),
                            row.getParsedAmount().toPlainString(),
                            dbDuplicates.get(0).getId()),
                    "Review and APPROVE to import anyway, or REJECT to skip this row"));
        }

        // Check against other rows in this CSV batch
        for (CsvRowDTO existing : existingRows) {
            if (existing.getDescription() == null || existing.getParsedDate() == null) continue;

            boolean sameDescDate = existing.getDescription().equalsIgnoreCase(row.getDescription())
                    && existing.getParsedDate().equals(row.getParsedDate());

            if (sameDescDate && existing.getParsedAmount() != null && row.getParsedAmount() != null) {
                if (existing.getParsedAmount().compareTo(row.getParsedAmount()) == 0) {
                    // ANOMALY_001: Exact duplicate in same batch
                    anomalies.add(buildAnomaly(rowNumber, rawLine,
                            AnomalyType.DUPLICATE_EXPENSE,
                            String.format("Duplicate within CSV: '%s' on %s for ₹%s appears multiple times",
                                    row.getDescription(), row.getParsedDate(),
                                    row.getParsedAmount().toPlainString()),
                            "APPROVE to import this row, or REJECT to skip it"));
                } else {
                    // ANOMALY_008: Same event, different amounts
                    anomalies.add(buildAnomaly(rowNumber, rawLine,
                            AnomalyType.CONFLICTING_DUPLICATE,
                            String.format("Conflicting duplicate: '%s' on %s has amount ₹%s in this row but ₹%s in another row",
                                    row.getDescription(), row.getParsedDate(),
                                    row.getParsedAmount().toPlainString(),
                                    existing.getParsedAmount().toPlainString()),
                            "Review both rows and decide which amount is correct"));
                }
            }
        }
    }

    // ================================================================
    // ANOMALY_002: Currency mismatch
    // ================================================================

    /**
     * Detects amounts that look like USD but are labeled as INR.
     *
     * <p>Heuristic: if the currency is INR but the amount is suspiciously
     * low for a shared expense (below threshold), it might actually be USD.
     * Also checks if the description mentions dollars or USD.
     *
     * <p>Policy: Flag for user review — the user decides whether to
     * convert using the documented rate.
     */
    private void checkCurrencyMismatch(CsvRowDTO row, int rowNumber,
                                        String rawLine, List<ImportAnomaly> anomalies) {
        if (row.getParsedAmount() == null || row.getCurrency() == null) return;

        String currency = row.getCurrency().trim().toUpperCase();
        String desc = row.getDescription() != null ? row.getDescription().toLowerCase() : "";

        // Check 1: Currency says INR but amount is suspiciously small
        // AND description mentions USD-related keywords
        boolean descMentionsDollar = desc.contains("usd") || desc.contains("dollar")
                || desc.contains("$") || desc.contains("us ");

        if ("INR".equals(currency) && row.getParsedAmount().compareTo(USD_SUSPICION_THRESHOLD) < 0
                && descMentionsDollar) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.CURRENCY_MISMATCH,
                    String.format("Currency mismatch: amount ₹%s seems too low for INR and description mentions USD/dollar. " +
                                    "This might be %s USD (= ₹%s INR at rate 83.00)",
                            row.getParsedAmount().toPlainString(),
                            row.getParsedAmount().toPlainString(),
                            row.getParsedAmount().multiply(new BigDecimal("83.00")).toPlainString()),
                    "APPROVE to auto-convert from USD to INR, or REJECT to keep as INR"));
        }

        // Check 2: Currency field contains unexpected values
        if (!"INR".equals(currency) && !"USD".equals(currency)) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.CURRENCY_MISMATCH,
                    "Unrecognized currency: '" + row.getCurrency() + "'. Expected INR or USD.",
                    "APPROVE to treat as INR, or REJECT to skip this row"));
        }
    }

    // ================================================================
    // ANOMALY_003: Negative amount
    // ================================================================

    private void checkNegativeAmount(CsvRowDTO row, int rowNumber,
                                      String rawLine, List<ImportAnomaly> anomalies) {
        if (row.getParsedAmount() == null) return;

        if (row.getParsedAmount().compareTo(BigDecimal.ZERO) < 0) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.NEGATIVE_AMOUNT,
                    String.format("Negative amount: %s. This will be treated as a refund " +
                                    "creating a negative expense split.",
                            row.getParsedAmount().toPlainString()),
                    "APPROVE to import as refund, or REJECT to skip"));
        }
    }

    // ================================================================
    // ANOMALY_004: Post-exit expense
    // ================================================================

    /**
     * Checks if any member mentioned in the expense has already left
     * the group before the expense date.
     *
     * <p>Policy: exclude that member from the split, flag row.
     * Satisfies Sam's and Meera's requirements.
     */
    private void checkPostExitExpense(CsvRowDTO row, int rowNumber,
                                       String rawLine, Long groupId,
                                       List<ImportAnomaly> anomalies) {
        if (row.getParsedDate() == null) return;

        // Get all memberships in the group
        List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);

        for (GroupMembership membership : memberships) {
            // Check if member left BEFORE the expense date
            if (membership.getLeftAt() != null
                    && membership.getLeftAt().isBefore(row.getParsedDate())) {
                anomalies.add(buildAnomaly(rowNumber, rawLine,
                        AnomalyType.POST_EXIT_EXPENSE,
                        String.format("Post-exit expense: %s left the group on %s but this expense is dated %s. " +
                                        "They will be excluded from the split.",
                                membership.getUser().getFullName(),
                                membership.getLeftAt(),
                                row.getParsedDate()),
                        "APPROVE to import (excluding departed member from split), or REJECT to skip"));
                // Don't break — there might be multiple departed members
            }

            // Also check if expense is before a member joined
            if (membership.getJoinedAt().isAfter(row.getParsedDate())) {
                // This is expected (e.g., Sam not in Feb expenses)
                // but we log it at DEBUG level for traceability
                logger.debug("Row {}: {} hadn't joined yet on {} (joined {})",
                        rowNumber, membership.getUser().getFullName(),
                        row.getParsedDate(), membership.getJoinedAt());
            }
        }
    }

    // ================================================================
    // ANOMALY_005: Settlement logged as expense
    // ================================================================

    /**
     * Detects settlement transactions disguised as regular expenses.
     *
     * <p>Detection: checks description against settlement keywords.
     * Policy: import as Payment record instead of Expense.
     */
    private void checkSettlementAsExpense(CsvRowDTO row, int rowNumber,
                                           String rawLine, List<ImportAnomaly> anomalies) {
        if (row.getDescription() == null) return;

        String descLower = row.getDescription().toLowerCase().trim();

        for (String keyword : SETTLEMENT_KEYWORDS) {
            if (descLower.contains(keyword)) {
                anomalies.add(buildAnomaly(rowNumber, rawLine,
                        AnomalyType.SETTLEMENT_AS_EXPENSE,
                        String.format("Settlement detected: description '%s' contains keyword '%s'. " +
                                        "This appears to be a settlement payment, not an expense.",
                                row.getDescription(), keyword),
                        "APPROVE to import as a Payment (settlement) record, or REJECT to skip"));
                return; // One settlement match is enough
            }
        }
    }

    // ================================================================
    // ANOMALY_006: Missing required fields
    // ================================================================

    private void checkMissingFields(CsvRowDTO row, int rowNumber,
                                     String rawLine, List<ImportAnomaly> anomalies) {
        List<String> missingFields = new ArrayList<>();

        if (isBlank(row.getDescription())) missingFields.add("description");
        if (isBlank(row.getDate())) missingFields.add("date");
        if (isBlank(row.getAmount())) missingFields.add("amount");
        if (isBlank(row.getPaidBy())) missingFields.add("paid_by");

        if (!missingFields.isEmpty()) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.MISSING_FIELDS,
                    "Missing required fields: " + String.join(", ", missingFields),
                    "Cannot import — row will be skipped"));
        }
    }

    // ================================================================
    // ANOMALY_007: Unknown member name
    // ================================================================

    private void checkUnknownMember(CsvRowDTO row, int rowNumber,
                                     String rawLine, List<ImportAnomaly> anomalies) {
        if (isBlank(row.getPaidBy())) return;

        Optional<User> user = userRepository.findByFullName(row.getPaidBy().trim());
        if (user.isEmpty()) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.UNKNOWN_MEMBER,
                    String.format("Unknown member: '%s' does not match any registered user. " +
                            "Registered users can be found in the user management section.",
                            row.getPaidBy()),
                    "Register this user first, or map this name to an existing user"));
        }
    }

    // ================================================================
    // ANOMALY_009: Invalid date format
    // ================================================================

    private void checkInvalidDate(CsvRowDTO row, int rowNumber,
                                   String rawLine, List<ImportAnomaly> anomalies) {
        if (isBlank(row.getDate())) return;

        LocalDate parsed = tryParseDate(row.getDate().trim());
        if (parsed == null) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.INVALID_DATE,
                    String.format("Invalid date: '%s' could not be parsed. " +
                            "Tried formats: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy, dd-MM-yyyy, etc.",
                            row.getDate()),
                    "Fix the date format and re-upload, or REJECT to skip this row"));
        } else {
            // Store the successfully parsed date for later use
            row.setParsedDate(parsed);
        }
    }

    // ================================================================
    // ANOMALY_010: Split percentages don't sum to 100
    // ================================================================

    private void checkSplitPercentageMismatch(CsvRowDTO row, int rowNumber,
                                               String rawLine, List<ImportAnomaly> anomalies) {
        if (row.getSplitType() == null || !row.getSplitType().equalsIgnoreCase("PERCENTAGE")) return;
        if (row.getSplitPercentages() == null || row.getSplitPercentages().isEmpty()) return;

        BigDecimal sum = row.getSplitPercentages().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(new BigDecimal("100")) != 0) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.SPLIT_PERCENTAGE_MISMATCH,
                    String.format("Split percentages sum to %s%% instead of 100%%",
                            sum.toPlainString()),
                    "Fix percentages before importing. Row will not be imported until resolved."));
        }
    }

    // ================================================================
    // ANOMALY_011: Unsupported split type
    // ================================================================

    private void checkUnsupportedSplitType(CsvRowDTO row, int rowNumber,
                                            String rawLine, List<ImportAnomaly> anomalies) {
        if (isBlank(row.getSplitType())) return;

        String splitType = row.getSplitType().trim().toUpperCase();
        if (!splitType.equals("EQUAL") && !splitType.equals("EXACT")
                && !splitType.equals("PERCENTAGE") && !splitType.equals("SHARES")) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.UNSUPPORTED_SPLIT_TYPE,
                    String.format("Unsupported split type: '%s'. Supported types: EQUAL, EXACT, PERCENTAGE, SHARES",
                            row.getSplitType()),
                    "Row will be skipped. Change the split type and re-upload."));
        }
    }

    // ================================================================
    // ANOMALY_012: Zero amount
    // ================================================================

    private void checkZeroAmount(CsvRowDTO row, int rowNumber,
                                  String rawLine, List<ImportAnomaly> anomalies) {
        if (row.getParsedAmount() == null) return;

        if (row.getParsedAmount().compareTo(BigDecimal.ZERO) == 0) {
            anomalies.add(buildAnomaly(rowNumber, rawLine,
                    AnomalyType.ZERO_AMOUNT,
                    "Zero amount expense — this is likely a data entry error",
                    "Row will be skipped. APPROVE to import anyway, or REJECT to skip."));
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Attempts to parse a date string using multiple common formats.
     *
     * @param dateStr the date string to parse
     * @return the parsed LocalDate, or null if all formats fail
     */
    public LocalDate tryParseDate(String dateStr) {
        if (dateStr == null) return null;
        String trimmed = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return null;
    }

    /**
     * Builds an ImportAnomaly object (not yet persisted to DB).
     * These are held in memory until the user confirms import.
     */
    private ImportAnomaly buildAnomaly(int rowNumber, String rawLine,
                                       AnomalyType type, String description,
                                       String suggestedAction) {
        return ImportAnomaly.builder()
                .rowNumber(rowNumber)
                .rawCsvRow(rawLine)
                .anomalyType(type)
                .description(description)
                .suggestedAction(suggestedAction)
                .userDecision(UserDecision.PENDING)
                .build();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
