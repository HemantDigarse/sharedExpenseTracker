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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA entity representing a shared expense group.
 *
 * <p>Maps to the {@code expense_groups} table created in Flyway migration V2.
 * Named "expense_groups" (not "groups") to avoid the PostgreSQL reserved keyword.
 *
 * <p>A group contains members (via {@link GroupMembership}) and expenses.
 * Examples: "Flat Expenses Feb-May", "Goa Trip".
 *
 * <p>IMPORTANT: Never expose this entity directly in API responses.
 * Always use {@link GroupDTO}.
 */
@Entity
@Table(name = "expense_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Group name displayed in the UI.
     * Examples: "Flat Expenses", "Goa Trip"
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Optional description for additional context.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The user who created this group.
     * LAZY fetch: creator is only loaded when explicitly accessed,
     * avoiding unnecessary JOINs on group list queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
