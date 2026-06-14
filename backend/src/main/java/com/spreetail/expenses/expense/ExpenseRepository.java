package com.spreetail.expenses.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Expense} entities.
 *
 * <p>Contains queries for expense listing, duplicate detection
 * (used by AnomalyDetector), and balance calculation support.
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Finds all expenses in a group, ordered by date (newest first).
     * Used by the expense list view in the group detail page.
     */
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);

    /**
     * Finds all non-settlement expenses in a group.
     * Settlements are tracked in the payments table — this query
     * excludes them from the regular expense list.
     */
    List<Expense> findByGroupIdAndIsSettlementFalseOrderByExpenseDateDesc(Long groupId);

    /**
     * Duplicate detection query for CSV import (ANOMALY_001).
     *
     * <p>Checks if an expense with the same description, date, and amount
     * already exists in the group. Used by AnomalyDetector to flag
     * potential duplicates.
     *
     * <p>Note: This is a "fuzzy" duplicate check — it's possible for
     * two legitimate expenses with the same description, date, and amount
     * to exist. That's why duplicates are flagged for user review
     * (Meera's requirement) rather than auto-rejected.
     */
    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId " +
            "AND LOWER(e.description) = LOWER(:description) " +
            "AND e.expenseDate = :date " +
            "AND e.amount = :amount")
    List<Expense> findDuplicates(
            @Param("groupId") Long groupId,
            @Param("description") String description,
            @Param("date") LocalDate date,
            @Param("amount") BigDecimal amount);

    /**
     * Finds all expenses from a specific import session.
     * Used by the import report to show what was imported.
     */
    List<Expense> findByImportSessionId(Long importSessionId);

    /**
     * Finds all expenses in a group paid by a specific user.
     * Used in balance calculations to determine what a user paid.
     */
    List<Expense> findByGroupIdAndPaidById(Long groupId, Long userId);
}
