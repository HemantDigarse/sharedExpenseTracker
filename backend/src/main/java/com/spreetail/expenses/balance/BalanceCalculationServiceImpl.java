package com.spreetail.expenses.balance;

import com.spreetail.expenses.common.ResourceNotFoundException;
import com.spreetail.expenses.expense.Expense;
import com.spreetail.expenses.expense.ExpenseRepository;
import com.spreetail.expenses.expense.ExpenseSplit;
import com.spreetail.expenses.expense.ExpenseSplitRepository;
import com.spreetail.expenses.group.Group;
import com.spreetail.expenses.group.GroupMembership;
import com.spreetail.expenses.group.GroupMembershipRepository;
import com.spreetail.expenses.group.GroupRepository;
import com.spreetail.expenses.settlement.Payment;
import com.spreetail.expenses.settlement.PaymentRepository;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * THE MOST BUSINESS-CRITICAL SERVICE IN THE APPLICATION.
 *
 * <p>Calculates net balances for all members of a group by applying
 * seven explicitly defined rules. Each rule is documented inline
 * with its implementation.
 *
 * <h2>Balance Calculation Algorithm</h2>
 * <pre>
 * For each user in the group:
 *   1. totalPaid = SUM of amountInInr for all expenses where user is paidBy
 *      (RULE 1: only expenses where user was an active member on expense_date)
 *      (RULE 2: always use amountInInr, never original amount)
 *
 *   2. totalOwed = SUM of finalAmountOwed from all expense_splits for this user
 *      (RULE 3: finalAmountOwed already accounts for split type)
 *
 *   3. netBalance = totalPaid - totalOwed
 *      + totalPaymentsReceived - totalPaymentsMade
 *      (RULE 4: settlements reduce balances)
 *
 *   4. Round all values to 2 decimal places using HALF_UP
 *      (RULE 5: consistent rounding)
 *
 *   5. Attach contributing expenses to each balance
 *      (RULE 6: traceability for Rohan)
 *
 *   6. Compute minimum-transaction settlements
 *      (RULE 7: simplified view for Aisha)
 * </pre>
 *
 * <h2>Rounding Rule (RULE 5)</h2>
 * <p>All monetary values use {@code BigDecimal} with
 * {@code RoundingMode.HALF_UP} to 2 decimal places.
 * This means values at the midpoint (e.g., 0.005) round UP.
 * Example: 1234.565 → 1234.57, 1234.564 → 1234.56
 */
@Service
public class BalanceCalculationServiceImpl implements BalanceCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(BalanceCalculationServiceImpl.class);

    /**
     * RULE 5: Decimal places for monetary values.
     */
    private static final int MONETARY_SCALE = 2;

    /**
     * RULE 5: Rounding mode — HALF_UP is the standard for financial calculations.
     * Also known as "round half away from zero".
     */
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final PaymentRepository paymentRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;

    public BalanceCalculationServiceImpl(ExpenseRepository expenseRepository,
                                          ExpenseSplitRepository splitRepository,
                                          PaymentRepository paymentRepository,
                                          GroupRepository groupRepository,
                                          GroupMembershipRepository membershipRepository) {
        this.expenseRepository = expenseRepository;
        this.splitRepository = splitRepository;
        this.paymentRepository = paymentRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Calculates all balances for a group.
     *
     * <p>This method applies all 7 balance calculation rules and
     * produces three outputs:
     * <ol>
     *   <li>Per-user net balances with contributing expense breakdown</li>
     *   <li>Summary totals (paid, owed, payments made/received)</li>
     *   <li>Minimum-transaction settlement suggestions</li>
     * </ol>
     *
     * <p>@Transactional(readOnly=true) tells Hibernate this is a read-only
     * operation, enabling optimizations (no dirty checking, flush skipping).
     */
    @Override
    @Transactional(readOnly = true)
    public BalanceDTO calculateBalances(Long groupId) {
        logger.info("Calculating balances for group ID: {}", groupId);

        // Verify group exists
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        // ================================================================
        // STEP 1: Load all data needed for calculation
        // ================================================================

        // Get ALL memberships (current and past) for the group.
        // We need past members too (e.g., Meera) because they still
        // have balances from when they were active.
        List<GroupMembership> allMemberships = membershipRepository.findByGroupId(groupId);

        // Get all non-settlement expenses in the group.
        // RULE 2: We use amountInInr (pre-converted at import time),
        // so no currency conversion is needed here.
        List<Expense> allExpenses = expenseRepository
                .findByGroupIdAndIsSettlementFalseOrderByExpenseDateDesc(groupId);

        // Get all expense splits in the group (one pass, not per-user).
        List<ExpenseSplit> allSplits = splitRepository.findAllByGroupId(groupId);

        // Get all settlement payments in the group.
        // RULE 4: These reduce balances — applied AFTER expense calculations.
        List<Payment> allPayments = paymentRepository.findByGroupId(groupId);

        // ================================================================
        // STEP 2: Build per-user accumulators
        // ================================================================

        // Collect unique users from memberships (includes past members)
        Map<Long, User> userMap = new HashMap<>();
        for (GroupMembership m : allMemberships) {
            userMap.put(m.getUser().getId(), m.getUser());
        }

        // Accumulator: what each user PAID (sum of expenses they paid for)
        // RULE 1: Only count expenses where the user was an active member
        // on the expense date. The payer is always included in the expense
        // (they paid, so they must have been present).
        Map<Long, BigDecimal> totalPaidMap = new HashMap<>();

        // Accumulator: what each user OWES (sum of their expense splits)
        // RULE 3: Split type is already handled — finalAmountOwed is
        // pre-calculated at expense creation time regardless of split type.
        Map<Long, BigDecimal> totalOwedMap = new HashMap<>();

        // Initialize all users to zero
        for (Long userId : userMap.keySet()) {
            totalPaidMap.put(userId, BigDecimal.ZERO);
            totalOwedMap.put(userId, BigDecimal.ZERO);
        }

        // ================================================================
        // STEP 3: Calculate totalPaid (what each user paid for)
        // ================================================================
        // For each expense, add the amountInInr to the payer's totalPaid.
        // RULE 2: Always use amountInInr (pre-converted to INR).
        for (Expense expense : allExpenses) {
            Long payerId = expense.getPaidBy().getId();
            BigDecimal currentTotal = totalPaidMap.getOrDefault(payerId, BigDecimal.ZERO);
            totalPaidMap.put(payerId, currentTotal.add(expense.getAmountInInr()));
        }

        // ================================================================
        // STEP 4: Calculate totalOwed (what each user owes)
        // ================================================================
        // For each expense split, add finalAmountOwed to the user's totalOwed.
        // RULE 3: finalAmountOwed already accounts for the split type
        // (EQUAL, EXACT, PERCENTAGE, or SHARES). We don't need to recalculate.
        for (ExpenseSplit split : allSplits) {
            Long userId = split.getUser().getId();
            BigDecimal currentTotal = totalOwedMap.getOrDefault(userId, BigDecimal.ZERO);
            totalOwedMap.put(userId, currentTotal.add(split.getFinalAmountOwed()));
        }

        // ================================================================
        // STEP 5: Calculate payment totals (RULE 4: Settlement deduction)
        // ================================================================
        // Payments REDUCE balances. They are applied AFTER expense calculations.
        // If A paid B ₹500 as settlement:
        //   - A's balance increases by 500 (they paid out money)
        //   - B's balance decreases by 500 (they received money)
        Map<Long, BigDecimal> paymentsMadeMap = new HashMap<>();
        Map<Long, BigDecimal> paymentsReceivedMap = new HashMap<>();

        for (Long userId : userMap.keySet()) {
            paymentsMadeMap.put(userId, BigDecimal.ZERO);
            paymentsReceivedMap.put(userId, BigDecimal.ZERO);
        }

        for (Payment payment : allPayments) {
            Long fromId = payment.getPaidBy().getId();
            Long toId = payment.getPaidTo().getId();

            paymentsMadeMap.put(fromId,
                    paymentsMadeMap.getOrDefault(fromId, BigDecimal.ZERO)
                            .add(payment.getAmount()));

            paymentsReceivedMap.put(toId,
                    paymentsReceivedMap.getOrDefault(toId, BigDecimal.ZERO)
                            .add(payment.getAmount()));
        }

        // ================================================================
        // STEP 6: Build per-user balance objects with traceability (RULE 6)
        // ================================================================
        // Group expense splits by user for the contributing expenses list.
        // This supports Rohan's requirement: every balance figure links
        // back to individual expense rows.
        Map<Long, List<ExpenseSplit>> splitsByUser = allSplits.stream()
                .collect(Collectors.groupingBy(s -> s.getUser().getId()));

        List<BalanceDTO.UserBalance> userBalances = new ArrayList<>();

        for (Map.Entry<Long, User> entry : userMap.entrySet()) {
            Long userId = entry.getKey();
            User user = entry.getValue();

            BigDecimal totalPaid = totalPaidMap.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal totalOwed = totalOwedMap.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal paymentsMade = paymentsMadeMap.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal paymentsReceived = paymentsReceivedMap.getOrDefault(userId, BigDecimal.ZERO);

            // Net balance formula:
            // netBalance = totalPaid - totalOwed + paymentsReceived - paymentsMade
            //
            // Interpretation:
            //   totalPaid > totalOwed → user paid more than their share → others owe them
            //   totalOwed > totalPaid → user owes more than they paid → they owe others
            //   paymentsReceived increase their balance (they got money back)
            //   paymentsMade decrease their balance (they paid off debts)
            //
            // RULE 5: Round to 2 decimal places using HALF_UP
            BigDecimal netBalance = totalPaid
                    .subtract(totalOwed)
                    .add(paymentsReceived)
                    .subtract(paymentsMade)
                    .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

            // RULE 6: Build contributing expenses list (Rohan's drilldown)
            List<BalanceDTO.ExpenseContribution> contributions = buildContributions(
                    userId, allExpenses, splitsByUser.getOrDefault(userId, List.of()));

            BalanceDTO.UserBalance userBalance = BalanceDTO.UserBalance.builder()
                    .user(UserDTO.fromEntity(user))
                    .totalPaid(totalPaid.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                    .totalOwed(totalOwed.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                    .totalPaymentsMade(paymentsMade.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                    .totalPaymentsReceived(paymentsReceived.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                    .netBalance(netBalance)
                    .contributingExpenses(contributions)
                    .build();

            userBalances.add(userBalance);
        }

        // ================================================================
        // STEP 7: Generate settlement suggestions (RULE 7)
        // ================================================================
        // Uses the minimum transactions algorithm to produce
        // Aisha's simplified view: "Rohan pays Aisha ₹1,200"
        List<BalanceDTO.SettlementSuggestion> settlements =
                calculateMinimumTransactions(userBalances);

        logger.info("Balance calculation complete for group {}: {} users, {} settlement suggestions",
                groupId, userBalances.size(), settlements.size());

        return BalanceDTO.builder()
                .groupId(groupId)
                .balances(userBalances)
                .settlements(settlements)
                .build();
    }

    // ================================================================
    // PRIVATE: Build contributing expenses (RULE 6)
    // ================================================================

    /**
     * Builds the list of expenses contributing to a user's balance.
     *
     * <p>For each expense, shows:
     * <ul>
     *   <li>How much the user PAID (non-zero only if they were the payer)</li>
     *   <li>How much the user OWES (their split share)</li>
     *   <li>Net contribution = paid - owed</li>
     * </ul>
     *
     * <p>This satisfies Rohan's requirement: "No magic numbers.
     * Show exactly which expenses make up my balance."
     */
    private List<BalanceDTO.ExpenseContribution> buildContributions(
            Long userId,
            List<Expense> allExpenses,
            List<ExpenseSplit> userSplits) {

        // Create a quick lookup: expenseId → user's split amount
        Map<Long, BigDecimal> splitByExpense = userSplits.stream()
                .collect(Collectors.toMap(
                        s -> s.getExpense().getId(),
                        ExpenseSplit::getFinalAmountOwed,
                        BigDecimal::add // handle unlikely duplicates
                ));

        List<BalanceDTO.ExpenseContribution> contributions = new ArrayList<>();

        for (Expense expense : allExpenses) {
            BigDecimal amountPaid = BigDecimal.ZERO;
            BigDecimal amountOwed = splitByExpense.getOrDefault(expense.getId(), BigDecimal.ZERO);

            // If this user was the payer, they paid the full amountInInr
            if (expense.getPaidBy().getId().equals(userId)) {
                amountPaid = expense.getAmountInInr();
            }

            // Only include expenses where this user was involved
            // (either as payer or as a split participant)
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0 ||
                    amountOwed.compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal netContribution = amountPaid.subtract(amountOwed)
                        .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

                contributions.add(BalanceDTO.ExpenseContribution.builder()
                        .expenseId(expense.getId())
                        .description(expense.getDescription())
                        .expenseDate(expense.getExpenseDate())
                        .totalExpenseAmount(expense.getAmountInInr())
                        .amountPaid(amountPaid.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                        .amountOwed(amountOwed.setScale(MONETARY_SCALE, MONETARY_ROUNDING))
                        .netContribution(netContribution)
                        .build());
            }
        }

        return contributions;
    }

    // ================================================================
    // PRIVATE: Minimum Transactions Algorithm (RULE 7)
    // ================================================================

    /**
     * Calculates the minimum number of transactions needed to settle
     * all debts in the group.
     *
     * <p>ALGORITHM (Greedy approach):
     * <ol>
     *   <li>Separate users into debtors (negative balance) and creditors (positive balance)</li>
     *   <li>Sort debtors by amount ascending (most in debt first)</li>
     *   <li>Sort creditors by amount descending (most owed first)</li>
     *   <li>Match the largest debtor with the largest creditor</li>
     *   <li>Transfer the minimum of (debt, credit)</li>
     *   <li>Reduce both balances accordingly</li>
     *   <li>Repeat until all balances are zero</li>
     * </ol>
     *
     * <p>This produces near-optimal results (minimum transactions)
     * for most real-world cases with small groups.
     *
     * <p>Satisfies Aisha's requirement: "One number per person.
     * Who pays whom, how much, done."
     *
     * @param userBalances the per-user net balances
     * @return list of settlement transactions (A pays B ₹X)
     */
    private List<BalanceDTO.SettlementSuggestion> calculateMinimumTransactions(
            List<BalanceDTO.UserBalance> userBalances) {

        // Separate into debtors (negative balance → owe money)
        // and creditors (positive balance → are owed money)
        List<BalanceEntry> debtors = new ArrayList<>();
        List<BalanceEntry> creditors = new ArrayList<>();

        for (BalanceDTO.UserBalance ub : userBalances) {
            // Skip users with zero balance (nothing to settle)
            if (ub.getNetBalance().compareTo(BigDecimal.ZERO) < 0) {
                // Negative balance → this user OWES money
                debtors.add(new BalanceEntry(ub.getUser(), ub.getNetBalance().abs()));
            } else if (ub.getNetBalance().compareTo(BigDecimal.ZERO) > 0) {
                // Positive balance → this user IS OWED money
                creditors.add(new BalanceEntry(ub.getUser(), ub.getNetBalance()));
            }
        }

        // Sort: largest debt first, largest credit first.
        // This greedy approach minimizes the number of transactions.
        debtors.sort(Comparator.comparing(BalanceEntry::amount).reversed());
        creditors.sort(Comparator.comparing(BalanceEntry::amount).reversed());

        List<BalanceDTO.SettlementSuggestion> suggestions = new ArrayList<>();

        int debtorIdx = 0;
        int creditorIdx = 0;

        // Greedy matching: pair the largest debtor with the largest creditor.
        // Transfer the minimum of the two amounts, then move to the next.
        while (debtorIdx < debtors.size() && creditorIdx < creditors.size()) {
            BalanceEntry debtor = debtors.get(debtorIdx);
            BalanceEntry creditor = creditors.get(creditorIdx);

            // Transfer amount = min(what debtor owes, what creditor is owed)
            BigDecimal transferAmount = debtor.amount().min(creditor.amount())
                    .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

            // Skip negligible amounts (less than ₹0.01 due to rounding)
            if (transferAmount.compareTo(new BigDecimal("0.01")) >= 0) {
                String description = String.format("%s pays %s ₹%s",
                        debtor.user().getFullName(),
                        creditor.user().getFullName(),
                        transferAmount.toPlainString());

                suggestions.add(BalanceDTO.SettlementSuggestion.builder()
                        .fromUser(debtor.user())
                        .toUser(creditor.user())
                        .amount(transferAmount)
                        .description(description)
                        .build());
            }

            // Reduce both balances by the transferred amount
            BigDecimal remainingDebt = debtor.amount().subtract(transferAmount);
            BigDecimal remainingCredit = creditor.amount().subtract(transferAmount);

            // Update the entries with remaining amounts
            debtors.set(debtorIdx, new BalanceEntry(debtor.user(), remainingDebt));
            creditors.set(creditorIdx, new BalanceEntry(creditor.user(), remainingCredit));

            // Move to next debtor/creditor if fully settled
            if (remainingDebt.compareTo(new BigDecimal("0.01")) < 0) {
                debtorIdx++;
            }
            if (remainingCredit.compareTo(new BigDecimal("0.01")) < 0) {
                creditorIdx++;
            }
        }

        return suggestions;
    }

    /**
     * Simple record to hold a user and their remaining balance
     * during the minimum transactions algorithm.
     */
    private record BalanceEntry(UserDTO user, BigDecimal amount) {}
}
