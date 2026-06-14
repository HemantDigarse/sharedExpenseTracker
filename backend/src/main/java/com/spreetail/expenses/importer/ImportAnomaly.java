package com.spreetail.expenses.importer;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for a single detected anomaly in a CSV import row.
 * Maps to {@code import_anomalies} table (V5 migration).
 *
 * <p>Each anomaly records:
 * <ul>
 *   <li>Which CSV row had the problem</li>
 *   <li>The original CSV data (verbatim)</li>
 *   <li>What type of anomaly was detected</li>
 *   <li>A human-readable description</li>
 *   <li>What the system suggests doing</li>
 *   <li>What the user decided (Meera's approval flow)</li>
 * </ul>
 */
@Entity
@Table(name = "import_anomalies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** CSV row number (1-indexed, excluding header) */
    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    /** Original CSV line preserved verbatim for audit trail */
    @Column(name = "raw_csv_row", nullable = false, columnDefinition = "TEXT")
    private String rawCsvRow;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false)
    private AnomalyType anomalyType;

    /** Human-readable description of the problem */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** System's recommended action */
    @Column(name = "suggested_action", columnDefinition = "TEXT")
    private String suggestedAction;

    /** User's decision: PENDING, APPROVED, or REJECTED */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_decision", nullable = false)
    @Builder.Default
    private UserDecision userDecision = UserDecision.PENDING;

    /** When the user made their decision (null while PENDING) */
    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}
