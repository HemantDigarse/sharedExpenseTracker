package com.spreetail.expenses.balance;

import com.spreetail.expenses.expense.ExpenseDTO;
import com.spreetail.expenses.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data Transfer Object for balance information.
 *
 * <p>Contains multiple views of the same balance data to satisfy
 * different user requirements:
 *
 * <ul>
 *   <li><b>Aisha's view</b>: {@link SettlementSuggestion} — "Who pays whom, how much, done."</li>
 *   <li><b>Rohan's view</b>: {@link UserBalance#contributingExpenses} — "Show exactly which
 *       expenses make up my balance."</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceDTO {

    /**
     * The group these balances belong to.
     */
    private Long groupId;

    /**
     * Per-user net balances within the group.
     * Positive = the user is owed money (paid more than their share).
     * Negative = the user owes money (paid less than their share).
     */
    private List<UserBalance> balances;

    /**
     * Simplified settlement suggestions (Aisha's requirement).
     * Uses the minimum transactions algorithm to reduce the number
     * of payments needed. One line per person maximum.
     */
    private List<SettlementSuggestion> settlements;

    // ================================================================
    // NESTED DTOs
    // ================================================================

    /**
     * A single user's net balance within a group.
     *
     * <p>Net balance = (total amount paid by user) - (total amount owed by user)
     * + (payments received) - (payments made)
     *
     * <p>Includes the list of individual expenses contributing to
     * this balance, supporting Rohan's drilldown requirement.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserBalance {
        private UserDTO user;

        /**
         * Total amount this user PAID for group expenses.
         * Sum of all expenses.amountInInr where paidBy = this user.
         */
        private BigDecimal totalPaid;

        /**
         * Total amount this user OWES across all expense splits.
         * Sum of all expenseSplits.finalAmountOwed for this user.
         */
        private BigDecimal totalOwed;

        /**
         * Total payments this user has MADE to settle debts.
         */
        private BigDecimal totalPaymentsMade;

        /**
         * Total payments this user has RECEIVED from others.
         */
        private BigDecimal totalPaymentsReceived;

        /**
         * Net balance after all expenses and settlements.
         * = totalPaid - totalOwed + totalPaymentsReceived - totalPaymentsMade
         *
         * Positive = others owe this user money.
         * Negative = this user owes money to others.
         */
        private BigDecimal netBalance;

        /**
         * Individual expenses contributing to this balance.
         * Enables Rohan's requirement: "Show exactly which expenses
         * make up my balance."
         *
         * Each entry shows the expense description, date, total amount,
         * and what this user owes for it.
         */
        private List<ExpenseContribution> contributingExpenses;
    }

    /**
     * Represents one expense's contribution to a user's balance.
     *
     * <p>Shows both what the user paid (if they were the payer)
     * and what they owe (their split share). The difference
     * is their net contribution from this expense.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseContribution {
        private Long expenseId;
        private String description;
        private java.time.LocalDate expenseDate;
        private BigDecimal totalExpenseAmount;

        /**
         * How much this user paid for this expense.
         * Non-zero only if this user was the payer.
         */
        private BigDecimal amountPaid;

        /**
         * How much this user owes for this expense (their split share).
         */
        private BigDecimal amountOwed;

        /**
         * Net contribution: amountPaid - amountOwed.
         * Positive = user paid more than their share for this expense.
         * Negative = user's share exceeds what they paid (or they didn't pay).
         */
        private BigDecimal netContribution;
    }

    /**
     * A simplified settlement suggestion (Aisha's requirement).
     *
     * <p>Format: "Person A pays Person B ₹X"
     * Minimizes the number of transactions needed to settle all debts.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementSuggestion {
        private UserDTO fromUser;
        private UserDTO toUser;
        private BigDecimal amount;

        /**
         * Human-readable settlement description.
         * Example: "Rohan pays Aisha ₹1,200.00"
         */
        private String description;
    }
}
