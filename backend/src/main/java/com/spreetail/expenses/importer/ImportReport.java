package com.spreetail.expenses.importer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO representing the full import report for a CSV upload session.
 *
 * <p>Returned to the frontend at multiple stages:
 * <ul>
 *   <li>Step 3 (surface to user): after parsing, before any DB writes</li>
 *   <li>Step 6 (import report): after import is complete</li>
 *   <li>Report page: viewable at any time after import</li>
 * </ul>
 *
 * <p>Format matches the specification exactly:
 * <pre>
 * {
 *   sessionId, filename, uploadedAt,
 *   totalRows, importedRows, skippedRows,
 *   anomalyCount,
 *   anomalies: [
 *     { rowNumber, rawRow, anomalyType, description, action, userDecision }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportReport {

    private Long sessionId;
    private String filename;
    private OffsetDateTime uploadedAt;
    private String status;
    private Integer totalRows;
    private Integer importedRows;
    private Integer skippedRows;
    private Integer anomalyCount;
    private List<AnomalyReport> anomalies;

    /**
     * Report entry for a single anomaly.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyReport {
        private Long anomalyId;
        private Integer rowNumber;
        private String rawRow;
        private String anomalyType;
        private String description;
        private String suggestedAction;
        private String userDecision;
        private OffsetDateTime decidedAt;

        public static AnomalyReport fromEntity(ImportAnomaly anomaly) {
            return AnomalyReport.builder()
                    .anomalyId(anomaly.getId())
                    .rowNumber(anomaly.getRowNumber())
                    .rawRow(anomaly.getRawCsvRow())
                    .anomalyType(anomaly.getAnomalyType().name())
                    .description(anomaly.getDescription())
                    .suggestedAction(anomaly.getSuggestedAction())
                    .userDecision(anomaly.getUserDecision().name())
                    .decidedAt(anomaly.getDecidedAt())
                    .build();
        }
    }

    /**
     * Builds a report from a session and its anomalies.
     */
    public static ImportReport fromSession(ImportSession session, List<ImportAnomaly> anomalies) {
        return ImportReport.builder()
                .sessionId(session.getId())
                .filename(session.getFilename())
                .uploadedAt(session.getUploadedAt())
                .status(session.getStatus().name())
                .totalRows(session.getTotalRows())
                .importedRows(session.getImportedRows())
                .skippedRows(session.getSkippedRows())
                .anomalyCount(session.getAnomalyCount())
                .anomalies(anomalies.stream()
                        .map(AnomalyReport::fromEntity)
                        .toList())
                .build();
    }
}
