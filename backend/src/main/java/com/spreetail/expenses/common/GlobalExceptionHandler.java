package com.spreetail.expenses.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>Catches exceptions thrown anywhere in the controller/service layer
 * and converts them into standardized {@link ApiResponse} objects with
 * appropriate HTTP status codes.
 *
 * <p>This ensures the frontend ALWAYS receives a consistent JSON structure,
 * even when things go wrong. No raw stack traces are ever sent to clients.
 *
 * <p>Exception → HTTP Status mapping:
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link BusinessRuleException} → 400 Bad Request</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (validation)</li>
 *   <li>{@link BadCredentialsException} → 401 Unauthorized</li>
 *   <li>{@link MaxUploadSizeExceededException} → 413 Payload Too Large</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles resource not found (404).
     * Thrown when a query-by-ID or query-by-unique-field returns no results.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles business rule violations (400).
     * Thrown when a domain invariant is broken (e.g., split percentages != 100).
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        logger.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles Bean Validation failures (400).
     * Triggered by @Valid on controller method parameters when DTO
     * fields fail @NotNull, @Email, @Size, etc. constraints.
     *
     * <p>Extracts individual field-level errors and returns them
     * as a list in the 'errors' array of the ApiResponse.
     * Example: ["email: must not be blank", "amount: must be positive"]
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        // Extract field-level validation errors into human-readable messages.
        // Format: "fieldName: error message"
        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());

        logger.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", fieldErrors));
    }

    /**
     * Handles authentication failures (401).
     * Thrown by Spring Security when login credentials are incorrect.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    /**
     * Handles CSV file too large (413).
     * Triggered when the uploaded CSV exceeds spring.servlet.multipart.max-file-size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        logger.warn("File upload too large: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds the maximum allowed limit (10MB)"));
    }

    /**
     * Catch-all handler for unexpected exceptions (500).
     *
     * <p>IMPORTANT: We log the full stack trace for debugging but
     * return a generic message to the client. Never expose internal
     * error details (SQL queries, class names, etc.) to the frontend.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        // Log full stack trace for debugging — this goes to server logs, not to client
        logger.error("Unexpected error occurred", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Formats a validation FieldError into a human-readable string.
     * Format: "fieldName: error message"
     * Example: "email: must be a well-formed email address"
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
