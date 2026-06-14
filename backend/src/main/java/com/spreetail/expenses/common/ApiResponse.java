package com.spreetail.expenses.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standardized API response wrapper used by ALL endpoints.
 *
 * <p>Every API response in this application follows this structure:
 * <pre>
 * {
 *   "success": true/false,
 *   "data": { ... },          // null on error
 *   "message": "Human readable message",
 *   "errors": [ ... ]         // null/empty on success
 * }
 * </pre>
 *
 * <p>This ensures frontend code can always expect the same shape,
 * simplifying error handling in Axios interceptors.
 *
 * <p>Usage examples:
 * <pre>
 * // Success response
 * ApiResponse.success(userDto, "User registered successfully")
 *
 * // Error response
 * ApiResponse.error("Validation failed", List.of("Email is required", "Name is required"))
 * </pre>
 *
 * @param <T> the type of data payload (e.g., UserDTO, List&lt;ExpenseDTO&gt;)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Omit null fields from JSON (cleaner responses)
public class ApiResponse<T> {

    /**
     * Whether the request was processed successfully.
     * true = operation completed, data is present.
     * false = error occurred, check 'errors' list.
     */
    private boolean success;

    /**
     * The response payload. Contains the actual data on success.
     * Null on error responses.
     */
    private T data;

    /**
     * Human-readable message describing the result.
     * Always present for both success and error responses.
     * Examples: "User registered successfully", "Expense not found"
     */
    private String message;

    /**
     * List of error descriptions. Only populated on error responses.
     * Can contain multiple errors (e.g., validation failures).
     * Null or empty on success responses.
     */
    private List<String> errors;

    // ================================================================
    // FACTORY METHODS — Use these instead of the builder for common cases.
    // They enforce consistent success/error patterns.
    // ================================================================

    /**
     * Creates a success response with data and a message.
     *
     * @param data    the response payload
     * @param message human-readable success description
     * @param <T>     type of the payload
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * Creates a success response with data and a default message.
     *
     * @param data the response payload
     * @param <T>  type of the payload
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Operation completed successfully");
    }

    /**
     * Creates an error response with a message and list of specific errors.
     *
     * @param message human-readable error summary
     * @param errors  list of specific error descriptions
     * @param <T>     type parameter (unused, payload is null)
     * @return ApiResponse with success=false
     */
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Creates an error response with just a message (no error list).
     *
     * @param message human-readable error description
     * @param <T>     type parameter (unused, payload is null)
     * @return ApiResponse with success=false
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
