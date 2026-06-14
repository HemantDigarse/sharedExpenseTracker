package com.spreetail.expenses.common;

/**
 * Custom exception thrown when a business rule is violated.
 *
 * <p>This exception is caught by {@link GlobalExceptionHandler} and
 * translated into an HTTP 400 (Bad Request) response with a
 * standardized {@link ApiResponse} body.
 *
 * <p>Examples of business rule violations:
 * <ul>
 *   <li>Adding an expense for a member who left the group before the expense date</li>
 *   <li>Split percentages that don't sum to 100%</li>
 *   <li>Recording a self-payment (paying yourself)</li>
 *   <li>Attempting to import a CSV with unresolved anomalies</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * if (percentageSum.compareTo(new BigDecimal("100")) != 0) {
 *     throw new BusinessRuleException("Split percentages must sum to 100%, got: " + percentageSum);
 * }
 * </pre>
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Creates a BusinessRuleException with the specified message.
     *
     * @param message description of the violated business rule
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
