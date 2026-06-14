package com.spreetail.expenses.settlement;

import com.spreetail.expenses.group.Group;
import com.spreetail.expenses.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a settlement payment between two users.
 *
 * <p>Maps to the {@code payments} table created in Flyway migration V4.
 *
 * <p>Payments are NOT expenses — they REDUCE outstanding balances.
 * When person A pays person B ₹500:
 * <ul>
 *   <li>A's net balance increases by 500 (they paid out money)</li>
 *   <li>B's net balance decreases by 500 (they received money)</li>
 * </ul>
 *
 * <p>Balance calculation rule (RULE 4):
 * Payments are applied AFTER calculating expense-based balances.
 *
 * <p>All payment amounts are in INR (the base currency for all calculations).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The group this payment belongs to.
     * Payments only make sense within a group's shared expenses.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /**
     * The user making the payment (the one who owes money).
     * Example: Rohan pays Aisha → paidBy = Rohan
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    /**
     * The user receiving the payment (the one who is owed money).
     * Example: Rohan pays Aisha → paidTo = Aisha
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_to", nullable = false)
    private User paidTo;

    /**
     * Payment amount in INR.
     * Must be positive (DB constraint: CHECK amount > 0).
     * NUMERIC(15,2) maps to Java BigDecimal.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Date the payment was made.
     * For audit trail — does NOT affect balance calculations
     * (unlike expense_date which determines membership eligibility).
     */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /**
     * Optional notes for context.
     * Example: "Settling March expenses", "Venmo transfer"
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
