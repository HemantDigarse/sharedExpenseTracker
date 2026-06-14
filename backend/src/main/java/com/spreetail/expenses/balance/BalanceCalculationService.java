package com.spreetail.expenses.balance;

/**
 * Service interface for balance calculation operations.
 *
 * <p>Abstracted as an interface for testability (architecture rule #7).
 * This is the most business-critical service — unit tests should
 * cover every calculation path.
 *
 * @see BalanceCalculationServiceImpl
 */
public interface BalanceCalculationService {

    /**
     * Calculates all balances for a group, including:
     * <ul>
     *   <li>Per-user net balances (what each person owes / is owed)</li>
     *   <li>Contributing expense breakdown (Rohan's drilldown)</li>
     *   <li>Simplified settlement suggestions (Aisha's one-liner)</li>
     * </ul>
     *
     * <p>Business rules applied:
     * <ol>
     *   <li>RULE 1: Membership date filter — only active members on expense date</li>
     *   <li>RULE 2: Currency normalization — all calculations in INR</li>
     *   <li>RULE 3: Split type handling — EQUAL, EXACT, PERCENTAGE, SHARES</li>
     *   <li>RULE 4: Settlement deduction — payments reduce balances</li>
     *   <li>RULE 5: Rounding — BigDecimal HALF_UP to 2 decimal places</li>
     *   <li>RULE 6: Traceability — link balances to individual expenses</li>
     *   <li>RULE 7: Minimum transactions — simplify settlements</li>
     * </ol>
     *
     * @param groupId the group to calculate balances for
     * @return BalanceDTO with per-user balances and settlement suggestions
     */
    BalanceDTO calculateBalances(Long groupId);
}
