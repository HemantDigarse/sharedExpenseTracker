package com.spreetail.expenses.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service interface for expense operations.
 *
 * <p>Abstracted as an interface for testability (architecture rule #7).
 *
 * @see ExpenseServiceImpl
 */
public interface ExpenseService {

    /**
     * Creates a new expense with EQUAL split among active members.
     *
     * <p>Automatically:
     * <ol>
     *   <li>Determines eligible members (active on expense date)</li>
     *   <li>Converts currency to INR if needed</li>
     *   <li>Calculates equal splits</li>
     *   <li>Creates expense and split records</li>
     * </ol>
     *
     * @param groupId     the group ID
     * @param paidByUserId who paid
     * @param description expense description
     * @param amount      original amount
     * @param currency    original currency (INR or USD)
     * @param expenseDate when the expense was incurred
     * @return the created expense DTO with splits
     */
    ExpenseDTO createEqualExpense(Long groupId, Long paidByUserId,
                                   String description, BigDecimal amount,
                                   String currency, LocalDate expenseDate);

    /**
     * Creates a new expense with EXACT split amounts.
     *
     * @param groupId      the group ID
     * @param paidByUserId who paid
     * @param description  expense description
     * @param amount       total amount
     * @param currency     original currency
     * @param expenseDate  when the expense was incurred
     * @param exactSplits  map of userId → exact amount they owe
     * @return the created expense DTO with splits
     */
    ExpenseDTO createExactExpense(Long groupId, Long paidByUserId,
                                   String description, BigDecimal amount,
                                   String currency, LocalDate expenseDate,
                                   Map<Long, BigDecimal> exactSplits);

    /**
     * Creates a new expense with PERCENTAGE split.
     *
     * @param groupId          the group ID
     * @param paidByUserId     who paid
     * @param description      expense description
     * @param amount           total amount
     * @param currency         original currency
     * @param expenseDate      when the expense was incurred
     * @param percentageSplits map of userId → percentage
     * @return the created expense DTO with splits
     */
    ExpenseDTO createPercentageExpense(Long groupId, Long paidByUserId,
                                       String description, BigDecimal amount,
                                       String currency, LocalDate expenseDate,
                                       Map<Long, BigDecimal> percentageSplits);

    /**
     * Creates a new expense with SHARES split.
     *
     * @param groupId      the group ID
     * @param paidByUserId who paid
     * @param description  expense description
     * @param amount       total amount
     * @param currency     original currency
     * @param expenseDate  when the expense was incurred
     * @param sharesSplits map of userId → number of shares
     * @return the created expense DTO with splits
     */
    ExpenseDTO createSharesExpense(Long groupId, Long paidByUserId,
                                    String description, BigDecimal amount,
                                    String currency, LocalDate expenseDate,
                                    Map<Long, Integer> sharesSplits);

    /**
     * Gets an expense by ID with all its splits.
     */
    ExpenseDTO getExpenseById(Long expenseId);

    /**
     * Lists all expenses in a group.
     */
    List<ExpenseDTO> getExpensesByGroupId(Long groupId);
}
