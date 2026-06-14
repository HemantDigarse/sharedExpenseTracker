package com.spreetail.expenses.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Base entity class providing common fields for all JPA entities.
 *
 * <p>All entities in this application extend this class to inherit:
 * <ul>
 *   <li>{@code id} — auto-generated primary key (BIGSERIAL in PostgreSQL)</li>
 *   <li>{@code createdAt} — timestamp set once on creation</li>
 *   <li>{@code updatedAt} — timestamp updated on every modification</li>
 * </ul>
 *
 * <p>We use {@code @MappedSuperclass} instead of {@code @Inheritance}
 * because these fields are shared across ALL entities but there is no
 * polymorphic relationship between them (no "select all BaseEntities" query).
 *
 * <p>Timestamps use {@code OffsetDateTime} which maps to PostgreSQL's
 * {@code TIMESTAMPTZ} (timestamp with time zone) for correct timezone handling.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    /**
     * Auto-generated primary key.
     * Maps to BIGSERIAL (auto-incrementing BIGINT) in PostgreSQL.
     * IDENTITY strategy tells Hibernate to use the database's
     * auto-increment mechanism instead of a separate sequence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Timestamp when this record was first created.
     * Set automatically by {@link #onCreate()} — never updated after.
     * Maps to TIMESTAMPTZ (timestamp with time zone) in PostgreSQL.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp when this record was last modified.
     * Updated automatically by {@link #onUpdate()} on every save.
     * Maps to TIMESTAMPTZ (timestamp with time zone) in PostgreSQL.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * JPA lifecycle callback: sets both timestamps on initial persist.
     * Called automatically by Hibernate before INSERT.
     */
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * JPA lifecycle callback: updates the modification timestamp.
     * Called automatically by Hibernate before UPDATE.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
