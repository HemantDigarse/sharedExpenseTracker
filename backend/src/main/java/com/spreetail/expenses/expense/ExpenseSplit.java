package com.spreetail.expenses.expense;

import com.spreetail.expenses.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JPA entity representing one person's share of one expense.
 *
 * <p>Maps to the {@code expense_splits} table created in Flyway migration V3.
 *
 * <p>For an expense split among N people, there are N rows in this table.
 * Each row tracks how much one person owes for one expense.
 *
 * <p>Depending on the split type, different columns are populated:
 * <ul>
 *   <li>EQUAL:      only {@code finalAmountOwed} (auto-calculated)</li>
 *   <li>EXACT:      {@code shareAmount} + {@code finalAmountOwed}</li>
 *   <li>PERCENTAGE: {@code sharePercentage} + {@code finalAmountOwed}</li>
 *   <li>SHARES:     {@code shareUnits} + {@code finalAmountOwed}</li>
 * </ul>
 *
 * <p>{@code finalAmountOwed} is ALWAYS populated and ALWAYS in INR.
 * It is the single source of truth for balance calculations.
 *
 * <p>This supports Rohan's requirement: every balance figure can be
 * traced back to individual expense splits, showing exactly which
 * expenses contribute to a person's balance.
 */
@Entity
@Table(name = "expense_splits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The expense this split belongs to.
     * LAZY fetch to avoid loading the full expense when just
     * summing splits for balance calculations.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    /**
     * The user who owes this share of the expense.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Used for EXACT split type: the explicit amount this person owes.
     * NULL for other split types.
     * In the original expense currency (converted to INR in finalAmountOwed).
     */
    @Column(name = "share_amount", precision = 15, scale = 2)
    private BigDecimal shareAmount;

    /**
     * Used for PERCENTAGE split type: what percentage this person pays.
     * NULL for other split types.
     * All percentages for an expense must sum to exactly 100.00.
     * NUMERIC(5,2) allows values like 33.33, 25.00, etc.
     */
    @Column(name = "share_percentage", precision = 5, scale = 2)
    private BigDecimal sharePercentage;

    /**
     * Used for SHARES split type: number of shares this person has.
     * NULL for other split types.
     * Amount owed = (shareUnits / totalSharesInExpense) × expenseAmount
     */
    @Column(name = "share_units")
    private Integer shareUnits;

    /**
     * THE MOST IMPORTANT COLUMN IN THE BALANCE SYSTEM.
     *
     * <p>Always populated. Always in INR. Always calculated at creation time.
     * This is what BalanceCalculationService sums up to determine
     * how much each person owes.
     *
     * <p>Rounding: BigDecimal HALF_UP to 2 decimal places.
     *
     * <p>This column enables Rohan's requirement: every balance figure
     * can be drilled down to see which expense splits contribute to it.
     */
    @Column(name = "final_amount_owed", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmountOwed;
}
