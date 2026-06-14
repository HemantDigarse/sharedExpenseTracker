package com.spreetail.expenses.common;

/**
 * Custom exception thrown when a requested resource is not found.
 *
 * <p>This exception is caught by {@link GlobalExceptionHandler} and
 * translated into an HTTP 404 response with a standardized
 * {@link ApiResponse} body.
 *
 * <p>Usage:
 * <pre>
 * User user = userRepository.findById(id)
 *     .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
 * // Produces: "User not found with id: 42"
 * </pre>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a ResourceNotFoundException with a formatted message.
     *
     * @param resourceName the type of resource (e.g., "User", "Expense")
     * @param fieldName    the field used for lookup (e.g., "id", "email")
     * @param fieldValue   the value that was searched for
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
    }

    /**
     * Creates a ResourceNotFoundException with a custom message.
     *
     * @param message the error message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
