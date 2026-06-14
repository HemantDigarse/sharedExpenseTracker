package com.spreetail.expenses.expense;

import com.spreetail.expenses.currency.Currency;
import com.spreetail.expenses.group.Group;
import com.spreetail.expenses.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a shared expense.
 *
 * <p>Maps to the {@code expenses} table created in Flyway migration V3.
 *
 * <p>An expense has:
 * <ul>
 *   <li>A payer (who paid)</li>
 *   <li>An amount in original currency + pre-converted INR amount</li>
 *   <li>A split type determining how the cost is divided</li>
 *   <li>One or more {@link ExpenseSplit} records (one per participant)</li>
 * </ul>
 *
 * <p>MONETARY VALUES: All amounts use {@link BigDecimal} — NEVER Double/Float.
 * This prevents floating-point rounding errors in financial calculations.
 * Example: 0.1 + 0.2 = 0.30000000000000004 with Double, but exactly 0.3
 * with BigDecimal.
 *
 * <p>CURRENCY NORMALIZATION: {@code amountInInr} is always populated
 * at creation/import time. The balance engine only uses this column,
 * never the original {@code amount}, ensuring consistent INR-only
 * calculations. See Priya's requirement.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The group this expense belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /**
     * The user who paid for this expense.
     * This person is owed money by the other participants.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    /**
     * Human-readable description (e.g., "Groceries", "Electricity bill March").
     * Used by AnomalyDetector for duplicate detection and settlement detection.
     */
    @Column(nullable = false, length = 500)
    private String description;

    /**
     * Original expense amount in the original currency.
     * Example: $50 USD → amount = 50.00, currency = USD.
     * NUMERIC(15,2) in PostgreSQL, BigDecimal in Java.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Original currency of the expense.
     * Stored as a PostgreSQL ENUM string (not ordinal) for readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    /**
     * Pre-converted amount in INR.
     * ALWAYS populated, regardless of original currency:
     *   - If currency=INR: amountInInr = amount
     *   - If currency=USD: amountInInr = amount × USD_TO_INR_RATE
     *
     * THIS IS THE ONLY AMOUNT USED BY BalanceCalculationService.
     * Conversion happens once at creation/import, not on every query.
     */
    @Column(name = "amount_in_inr", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountInInr;

    /**
     * How this expense is divided among participants.
     * Determines which fields in ExpenseSplit are populated.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType;

    /**
     * Date the expense was incurred (NOT when it was entered).
     * Critical for membership filtering: only members active on
     * this date are included in the split.
     */
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /**
     * When this expense record was created in the system.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Flag for expenses that are actually settlements.
     * During CSV import, ANOMALY_005 detects these and routes
     * them to the payments table. This flag is a safety net.
     */
    @Column(name = "is_settlement", nullable = false)
    @Builder.Default
    private Boolean isSettlement = false;

    /**
     * Link to the CSV import session that created this expense.
     * NULL for manually-created expenses.
     */
    @Column(name = "import_session_id")
    private Long importSessionId;

    /**
     * The individual splits for this expense (one per participant).
     *
     * CascadeType.ALL: saving/deleting an expense also saves/deletes its splits.
     * orphanRemoval: removing a split from this list deletes it from DB.
     * LAZY fetch: splits are only loaded when explicitly accessed.
     */
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ExpenseSplit> splits = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        if (this.isSettlement == null) {
            this.isSettlement = false;
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Adds a split to this expense and sets the bidirectional relationship.
     *
     * <p>JPA requires both sides of a bidirectional relationship to be set.
     * This method ensures consistency by setting expense on the split.
     */
    public void addSplit(ExpenseSplit split) {
        splits.add(split);
        split.setExpense(this);
    }
}
