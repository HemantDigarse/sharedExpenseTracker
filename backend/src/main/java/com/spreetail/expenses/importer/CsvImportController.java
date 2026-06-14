package com.spreetail.expenses.importer;

import com.spreetail.expenses.common.ApiResponse;
import com.spreetail.expenses.common.ResourceNotFoundException;
import com.spreetail.expenses.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for the CSV import pipeline.
 *
 * <p>Implements the 3-step wizard flow:
 * <ol>
 *   <li>POST /api/groups/{groupId}/import/upload — upload CSV, get anomaly report</li>
 *   <li>POST /api/import/{sessionId}/decisions — submit approve/reject decisions</li>
 *   <li>POST /api/import/{sessionId}/confirm — confirm import, write to DB</li>
 * </ol>
 *
 * <p>Plus a read endpoint:
 * <ul>
 *   <li>GET /api/import/{sessionId}/report — view import report at any time</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final UserRepository userRepository;

    public CsvImportController(CsvImportService csvImportService,
                                UserRepository userRepository) {
        this.csvImportService = csvImportService;
        this.userRepository = userRepository;
    }

    /**
     * Step 1: Upload a CSV file for parsing and anomaly detection.
     *
     * <p>The file is parsed and all anomalies are detected, but
     * NO data is written to the expenses/payments tables yet.
     * The user must review anomalies and confirm before import proceeds.
     *
     * @param groupId the target group for import
     * @param file    the CSV file (multipart upload)
     * @return ImportReport with all detected anomalies
     */
    @PostMapping("/groups/{groupId}/import/upload")
    public ResponseEntity<ApiResponse<ImportReport>> uploadCsv(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);

        ImportReport report = csvImportService.parseAndDetect(file, groupId, userId);

        String message = report.getAnomalyCount() > 0
                ? report.getAnomalyCount() + " anomalies detected. Please review before importing."
                : "No anomalies detected. Ready to import.";

        return ResponseEntity.ok(ApiResponse.success(report, message));
    }

    /**
     * Step 2: Submit user decisions on flagged anomalies.
     *
     * <p>Meera's requirement: every flagged change must be explicitly
     * approved or rejected. This endpoint accepts a map of
     * anomalyId → decision (APPROVED/REJECTED).
     *
     * @param sessionId the import session ID
     * @param decisions map of anomaly ID → decision string
     * @return updated ImportReport with decisions recorded
     */
    @PostMapping("/import/{sessionId}/decisions")
    public ResponseEntity<ApiResponse<ImportReport>> submitDecisions(
            @PathVariable Long sessionId,
            @RequestBody Map<Long, String> decisions) {

        ImportReport report = csvImportService.submitDecisions(sessionId, decisions);
        return ResponseEntity.ok(
                ApiResponse.success(report, "Decisions recorded successfully"));
    }

    /**
     * Step 3: Confirm import — write approved rows to the database.
     *
     * <p>All pending anomalies must be resolved before this endpoint
     * can be called. The entire import is wrapped in a single transaction —
     * if any row fails, everything is rolled back.
     *
     * @param sessionId the import session ID
     * @param groupId   the target group
     * @return final ImportReport with import counts
     */
    @PostMapping("/groups/{groupId}/import/{sessionId}/confirm")
    public ResponseEntity<ApiResponse<ImportReport>> confirmImport(
            @PathVariable Long groupId,
            @PathVariable Long sessionId) {

        ImportReport report = csvImportService.confirmImport(sessionId, groupId);
        return ResponseEntity.ok(
                ApiResponse.success(report, "Import completed successfully"));
    }

    /**
     * View an import report at any time after import.
     *
     * <p>Shows all anomalies with user decisions, color-coded:
     * green=imported, yellow=flagged, red=skipped.
     */
    @GetMapping("/import/{sessionId}/report")
    public ResponseEntity<ApiResponse<ImportReport>> getReport(
            @PathVariable Long sessionId) {

        ImportReport report = csvImportService.getReport(sessionId);
        return ResponseEntity.ok(
                ApiResponse.success(report, "Import report retrieved"));
    }

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", userDetails.getUsername()))
                .getId();
    }
}
