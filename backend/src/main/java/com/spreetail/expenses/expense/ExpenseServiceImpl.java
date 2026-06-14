package com.spreetail.expenses.expense;

import com.spreetail.expenses.common.BusinessRuleException;
import com.spreetail.expenses.common.ResourceNotFoundException;
import com.spreetail.expenses.currency.Currency;
import com.spreetail.expenses.currency.CurrencyConversionService;
import com.spreetail.expenses.group.Group;
import com.spreetail.expenses.group.GroupMembership;
import com.spreetail.expenses.group.GroupMembershipRepository;
import com.spreetail.expenses.group.GroupRepository;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ExpenseService}.
 *
 * <p>Handles expense creation with all four split types:
 * EQUAL, EXACT, PERCENTAGE, SHARES.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Currency conversion (USD → INR) at creation time</li>
 *   <li>Membership date validation (only active members can participate)</li>
 *   <li>Split calculation with proper BigDecimal rounding</li>
 *   <li>Validation of split totals (must match expense amount)</li>
 * </ul>
 *
 * <p>ROUNDING RULE: All monetary calculations use
 * {@code BigDecimal.HALF_UP} to 2 decimal places.
 * This is documented as a constant for traceability.
 */
@Service
public class ExpenseServiceImpl implements ExpenseService {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseServiceImpl.class);

    /**
     * Number of decimal places for all monetary values.
     * All amounts are rounded to 2 decimal places (e.g., ₹1,234.56).
     */
    private static final int MONETARY_SCALE = 2;

    /**
     * Rounding mode for all monetary calculations.
     * HALF_UP: values at the midpoint (0.005) round up.
     * This is the standard for financial applications.
     * See DECISIONS.md: "Rounding rule choice"
     */
    private static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CurrencyConversionService currencyConversionService;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                               ExpenseSplitRepository splitRepository,
                               GroupRepository groupRepository,
                               GroupMembershipRepository membershipRepository,
                               UserRepository userRepository,
                               CurrencyConversionService currencyConversionService) {
        this.expenseRepository = expenseRepository;
        this.splitRepository = splitRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.currencyConversionService = currencyConversionService;
    }

    // ================================================================
    // EQUAL SPLIT
    // ================================================================

    /**
     * Creates an expense split equally among all active members on the expense date.
     *
     * <p>Split calculation:
     * {@code amountInInr ÷ numberOfEligibleMembers}
     *
     * <p>Membership filter: only members with
     * {@code joinedAt <= expenseDate AND (leftAt IS NULL OR leftAt >= expenseDate)}
     * are included in the split.
     *
     * <p>@Transactional ensures the expense and all its splits are saved
     * atomically. If any split fails, the entire expense is rolled back.
     */
    @Override
    @Transactional
    public ExpenseDTO createEqualExpense(Long groupId, Long paidByUserId,
                                          String description, BigDecimal amount,
                                          String currency, LocalDate expenseDate) {
        logger.info("Creating EQUAL expense: group={}, paidBy={}, amount={} {}",
                groupId, paidByUserId, amount, currency);

        // Resolve entities
        Group group = findGroupOrThrow(groupId);
        User paidBy = findUserOrThrow(paidByUserId);
        Currency expenseCurrency = Currency.valueOf(currency);

        // Convert to INR (if needed)
        BigDecimal amountInInr = currencyConversionService.convert(
                amount, expenseCurrency, Currency.INR, expenseDate);

        // Find eligible members (active on the expense date)
        List<GroupMembership> activeMembers = membershipRepository
                .findActiveMembersOnDate(groupId, expenseDate);

        if (activeMembers.isEmpty()) {
            throw new BusinessRuleException(
                    "No active members found in group on date: " + expenseDate);
        }

        // Build the expense entity
        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(description)
                .amount(amount)
                .currency(expenseCurrency)
                .amountInInr(amountInInr)
                .splitType(SplitType.EQUAL)
                .expenseDate(expenseDate)
                .build();

        // Calculate equal split amount.
        // amountInInr ÷ memberCount, rounded to 2 decimal places using HALF_UP.
        int memberCount = activeMembers.size();
        BigDecimal splitAmount = amountInInr.divide(
                BigDecimal.valueOf(memberCount), MONETARY_SCALE, MONETARY_ROUNDING);

        // Create a split record for each eligible member.
        for (GroupMembership membership : activeMembers) {
            ExpenseSplit split = ExpenseSplit.builder()
                    .user(membership.getUser())
                    .finalAmountOwed(splitAmount)
                    .build();
            expense.addSplit(split);
        }

        // Save expense + all splits in a single transaction.
        // CascadeType.ALL on the splits relationship ensures splits are saved
        // automatically when the expense is saved.
        Expense savedExpense = expenseRepository.save(expense);
        logger.info("EQUAL expense created: ID={}, splits={}", savedExpense.getId(), memberCount);

        return ExpenseDTO.fromEntityWithSplits(savedExpense);
    }

    // ================================================================
    // EXACT SPLIT
    // ================================================================

    /**
     * Creates an expense with explicit amounts per person.
     *
     * <p>Validation: the sum of all exact amounts must equal the
     * expense's amountInInr. If not, a BusinessRuleException is thrown.
     */
    @Override
    @Transactional
    public ExpenseDTO createExactExpense(Long groupId, Long paidByUserId,
                                          String description, BigDecimal amount,
                                          String currency, LocalDate expenseDate,
                                          Map<Long, BigDecimal> exactSplits) {
        logger.info("Creating EXACT expense: group={}, paidBy={}, amount={} {}",
                groupId, paidByUserId, amount, currency);

        Group group = findGroupOrThrow(groupId);
        User paidBy = findUserOrThrow(paidByUserId);
        Currency expenseCurrency = Currency.valueOf(currency);

        BigDecimal amountInInr = currencyConversionService.convert(
                amount, expenseCurrency, Currency.INR, expenseDate);

        // Validate: sum of exact splits must equal total amount
        BigDecimal splitSum = exactSplits.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        if (splitSum.compareTo(amountInInr) != 0) {
            throw new BusinessRuleException(
                    "Exact split amounts sum to " + splitSum +
                            " but expense amount is " + amountInInr +
                            ". They must be equal.");
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(description)
                .amount(amount)
                .currency(expenseCurrency)
                .amountInInr(amountInInr)
                .splitType(SplitType.EXACT)
                .expenseDate(expenseDate)
                .build();

        // Create split for each specified user
        for (Map.Entry<Long, BigDecimal> entry : exactSplits.entrySet()) {
            User splitUser = findUserOrThrow(entry.getKey());
            BigDecimal splitAmount = entry.getValue().setScale(MONETARY_SCALE, MONETARY_ROUNDING);

            ExpenseSplit split = ExpenseSplit.builder()
                    .user(splitUser)
                    .shareAmount(splitAmount)
                    .finalAmountOwed(splitAmount)
                    .build();
            expense.addSplit(split);
        }

        Expense savedExpense = expenseRepository.save(expense);
        logger.info("EXACT expense created: ID={}", savedExpense.getId());
        return ExpenseDTO.fromEntityWithSplits(savedExpense);
    }

    // ================================================================
    // PERCENTAGE SPLIT
    // ================================================================

    /**
     * Creates an expense where each person pays a percentage.
     *
     * <p>Validation: all percentages must sum to exactly 100.00.
     * If not, this would trigger ANOMALY_010 during CSV import.
     *
     * <p>Calculation: {@code amountInInr × (percentage ÷ 100)}
     */
    @Override
    @Transactional
    public ExpenseDTO createPercentageExpense(Long groupId, Long paidByUserId,
                                               String description, BigDecimal amount,
                                               String currency, LocalDate expenseDate,
                                               Map<Long, BigDecimal> percentageSplits) {
        logger.info("Creating PERCENTAGE expense: group={}, paidBy={}, amount={} {}",
                groupId, paidByUserId, amount, currency);

        Group group = findGroupOrThrow(groupId);
        User paidBy = findUserOrThrow(paidByUserId);
        Currency expenseCurrency = Currency.valueOf(currency);

        BigDecimal amountInInr = currencyConversionService.convert(
                amount, expenseCurrency, Currency.INR, expenseDate);

        // Validate: percentages must sum to 100
        BigDecimal percentageSum = percentageSplits.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONETARY_SCALE, MONETARY_ROUNDING);

        BigDecimal hundred = new BigDecimal("100.00");
        if (percentageSum.compareTo(hundred) != 0) {
            throw new BusinessRuleException(
                    "Split percentages must sum to 100%, got: " + percentageSum + "%");
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(description)
                .amount(amount)
                .currency(expenseCurrency)
                .amountInInr(amountInInr)
                .splitType(SplitType.PERCENTAGE)
                .expenseDate(expenseDate)
                .build();

        for (Map.Entry<Long, BigDecimal> entry : percentageSplits.entrySet()) {
            User splitUser = findUserOrThrow(entry.getKey());
            BigDecimal percentage = entry.getValue();

            // Calculate: amountInInr × (percentage ÷ 100)
            BigDecimal finalAmount = amountInInr
                    .multiply(percentage)
                    .divide(hundred, MONETARY_SCALE, MONETARY_ROUNDING);

            ExpenseSplit split = ExpenseSplit.builder()
                    .user(splitUser)
                    .sharePercentage(percentage)
                    .finalAmountOwed(finalAmount)
                    .build();
            expense.addSplit(split);
        }

        Expense savedExpense = expenseRepository.save(expense);
        logger.info("PERCENTAGE expense created: ID={}", savedExpense.getId());
        return ExpenseDTO.fromEntityWithSplits(savedExpense);
    }

    // ================================================================
    // SHARES SPLIT
    // ================================================================

    /**
     * Creates an expense divided by shares.
     *
     * <p>Each member has a number of shares. Their owed amount is:
     * {@code amountInInr × (myShares ÷ totalShares)}
     *
     * <p>Example: Expense ₹10,000 with Aisha=2, Rohan=3, Priya=1 (total=6).
     * Aisha owes: (2/6) × 10,000 = ₹3,333.33
     */
    @Override
    @Transactional
    public ExpenseDTO createSharesExpense(Long groupId, Long paidByUserId,
                                           String description, BigDecimal amount,
                                           String currency, LocalDate expenseDate,
                                           Map<Long, Integer> sharesSplits) {
        logger.info("Creating SHARES expense: group={}, paidBy={}, amount={} {}",
                groupId, paidByUserId, amount, currency);

        Group group = findGroupOrThrow(groupId);
        User paidBy = findUserOrThrow(paidByUserId);
        Currency expenseCurrency = Currency.valueOf(currency);

        BigDecimal amountInInr = currencyConversionService.convert(
                amount, expenseCurrency, Currency.INR, expenseDate);

        // Calculate total shares
        int totalShares = sharesSplits.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalShares <= 0) {
            throw new BusinessRuleException("Total shares must be greater than 0");
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(description)
                .amount(amount)
                .currency(expenseCurrency)
                .amountInInr(amountInInr)
                .splitType(SplitType.SHARES)
                .expenseDate(expenseDate)
                .build();

        BigDecimal totalSharesBd = BigDecimal.valueOf(totalShares);

        for (Map.Entry<Long, Integer> entry : sharesSplits.entrySet()) {
            User splitUser = findUserOrThrow(entry.getKey());
            int units = entry.getValue();

            // Calculate: amountInInr × (myShares ÷ totalShares)
            BigDecimal finalAmount = amountInInr
                    .multiply(BigDecimal.valueOf(units))
                    .divide(totalSharesBd, MONETARY_SCALE, MONETARY_ROUNDING);

            ExpenseSplit split = ExpenseSplit.builder()
                    .user(splitUser)
                    .shareUnits(units)
                    .finalAmountOwed(finalAmount)
                    .build();
            expense.addSplit(split);
        }

        Expense savedExpense = expenseRepository.save(expense);
        logger.info("SHARES expense created: ID={}", savedExpense.getId());
        return ExpenseDTO.fromEntityWithSplits(savedExpense);
    }

    // ================================================================
    // QUERY METHODS
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public ExpenseDTO getExpenseById(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", expenseId));
        return ExpenseDTO.fromEntityWithSplits(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseDTO> getExpensesByGroupId(Long groupId) {
        // Verify group exists
        findGroupOrThrow(groupId);
        return expenseRepository.findByGroupIdAndIsSettlementFalseOrderByExpenseDateDesc(groupId)
                .stream()
                .map(ExpenseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private Group findGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
