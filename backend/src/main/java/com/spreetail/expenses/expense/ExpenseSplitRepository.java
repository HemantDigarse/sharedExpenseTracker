package com.spreetail.expenses.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ExpenseSplit} entities.
 *
 * <p>Contains queries for balance calculation (summing what each
 * person owes/is owed) and the balance breakdown drilldown
 * (Rohan's requirement).
 */
@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    /**
     * Finds all splits for a specific expense.
     * Used by the expense detail view.
     */
    List<ExpenseSplit> findByExpenseId(Long expenseId);

    /**
     * Finds all splits assigned to a specific user across all expenses in a group.
     *
     * <p>This is a KEY QUERY for balance calculations.
     * Each split represents an amount this user OWES for an expense.
     * Summing all finalAmountOwed gives the total the user owes.
     *
     * <p>Also supports Rohan's drilldown requirement: clicking a balance
     * shows the individual splits contributing to it.
     */
    @Query("SELECT es FROM ExpenseSplit es " +
            "JOIN es.expense e " +
            "WHERE e.group.id = :groupId " +
            "AND es.user.id = :userId " +
            "AND e.isSettlement = false")
    List<ExpenseSplit> findByGroupIdAndUserId(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId);

    /**
     * Finds all splits across all expenses in a group.
     *
     * <p>Used by BalanceCalculationService to compute all balances
     * in a single pass (more efficient than per-user queries).
     */
    @Query("SELECT es FROM ExpenseSplit es " +
            "JOIN es.expense e " +
            "WHERE e.group.id = :groupId " +
            "AND e.isSettlement = false")
    List<ExpenseSplit> findAllByGroupId(@Param("groupId") Long groupId);
}
