package com.spreetail.expenses.group;

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

import java.time.LocalDate;

/**
 * JPA entity representing a user's membership in a group.
 *
 * <p>Maps to the {@code group_memberships} table created in Flyway migration V2.
 *
 * <p>THIS IS THE MOST CRITICAL TABLE FOR BALANCE CALCULATIONS.
 * It implements time-ranged memberships using {@code joined_at} and
 * {@code left_at} dates to determine which members are affected by
 * each expense.
 *
 * <p>Business rules enforced by BalanceCalculationService:
 * <ul>
 *   <li>For an expense on date D, include member M only if:
 *       {@code M.joinedAt <= D AND (M.leftAt IS NULL OR M.leftAt >= D)}</li>
 *   <li>{@code leftAt = null} means the member is currently active</li>
 * </ul>
 *
 * <p>Assignment examples:
 * <ul>
 *   <li>Aisha, Rohan, Priya: joined Feb 1, leftAt = null (still active)</li>
 *   <li>Meera: joined Feb 1, leftAt = March 31 (moved out end of March)</li>
 *   <li>Sam: joined April 15, leftAt = null (moved in mid-April)</li>
 *   <li>Dev: joined for trip period only</li>
 * </ul>
 *
 * <p>Uses {@link LocalDate} (not OffsetDateTime) because membership
 * granularity is per-day. The assignment uses phrases like "mid-April"
 * and "end of March", not specific timestamps.
 */
@Entity
@Table(name = "group_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The group this membership belongs to.
     * LAZY fetch: avoids loading the full group entity
     * when we only need the membership dates.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /**
     * The user who is a member of the group.
     * LAZY fetch: avoids loading user data when just
     * checking membership existence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Date the member joined the group.
     *
     * <p>Used in balance calculation: expenses BEFORE this date
     * do not include this member in their split.
     *
     * <p>Example: Sam.joinedAt = April 15 means Sam is NOT included
     * in any February or March expense splits.
     * This directly satisfies Sam's requirement: "March expenses
     * shouldn't affect my balance."
     */
    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    /**
     * Date the member left the group.
     *
     * <p>NULL means the member is currently active (has not left).
     *
     * <p>Used in balance calculation: expenses AFTER this date
     * do not include this member in their split.
     *
     * <p>Example: Meera.leftAt = March 31 means Meera is NOT included
     * in any April or May expense splits.
     */
    @Column(name = "left_at")
    private LocalDate leftAt;

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Checks if this member is currently active (has not left the group).
     *
     * @return true if leftAt is null (member has not left)
     */
    public boolean isActive() {
        return this.leftAt == null;
    }

    /**
     * Checks if this member was active on a specific date.
     *
     * <p>This is the core membership check used by BalanceCalculationService:
     * {@code joinedAt <= date AND (leftAt IS NULL OR leftAt >= date)}
     *
     * <p>Edge cases:
     * <ul>
     *   <li>Expense on joinedAt date → INCLUDED (member was active that day)</li>
     *   <li>Expense on leftAt date → INCLUDED (member was still present that day)</li>
     *   <li>Expense day before joinedAt → EXCLUDED</li>
     *   <li>Expense day after leftAt → EXCLUDED</li>
     * </ul>
     *
     * @param date the expense date to check against
     * @return true if the member was active on this date
     */
    public boolean wasActiveOn(LocalDate date) {
        // Member must have joined on or before the expense date
        boolean joinedOnOrBefore = !this.joinedAt.isAfter(date);

        // Member must not have left, OR must have left on or after the expense date
        boolean notLeftYet = this.leftAt == null || !this.leftAt.isBefore(date);

        return joinedOnOrBefore && notLeftYet;
    }
}
