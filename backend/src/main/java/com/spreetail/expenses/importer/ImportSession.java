package com.spreetail.expenses.importer;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for CSV import sessions.
 * Maps to {@code import_sessions} table (V5 migration).
 * Tracks the lifecycle of a single CSV upload.
 */
@Entity
@Table(name = "import_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "imported_rows", nullable = false)
    @Builder.Default
    private Integer importedRows = 0;

    @Column(name = "skipped_rows", nullable = false)
    @Builder.Default
    private Integer skippedRows = 0;

    @Column(name = "anomaly_count", nullable = false)
    @Builder.Default
    private Integer anomalyCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ImportStatus status = ImportStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = OffsetDateTime.now();
    }
}
