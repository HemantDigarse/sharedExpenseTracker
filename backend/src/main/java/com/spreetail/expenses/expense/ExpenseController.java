package com.spreetail.expenses.expense;

import com.spreetail.expenses.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for expense management endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/groups/{groupId}/expenses       — create expense</li>
 *   <li>GET  /api/groups/{groupId}/expenses       — list expenses</li>
 *   <li>GET  /api/expenses/{expenseId}            — get expense detail</li>
 * </ul>
 *
 * <p>All endpoints require JWT authentication.
 */
@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // ================================================================
    // REQUEST DTO
    // ================================================================

    /**
     * Request DTO for creating a new expense.
     *
     * <p>The {@code splitType} determines which of the split-specific
     * fields must be provided:
     * <ul>
     *   <li>EQUAL: no additional fields (auto-calculated)</li>
     *   <li>EXACT: {@code exactSplits} (userId → amount)</li>
     *   <li>PERCENTAGE: {@code percentageSplits} (userId → percentage)</li>
     *   <li>SHARES: {@code sharesSplits} (userId → share count)</li>
     * </ul>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateExpenseRequest {
        @NotNull(message = "Payer user ID is required")
        private Long paidByUserId;

        @NotBlank(message = "Description is required")
        private String description;

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        private BigDecimal amount;

        @NotBlank(message = "Currency is required (INR or USD)")
        private String currency;

        @NotNull(message = "Expense date is required")
        private LocalDate expenseDate;

        @NotBlank(message = "Split type is required")
        private String splitType;

        // Split-type-specific fields (only one should be populated)
        private Map<Long, BigDecimal> exactSplits;
        private Map<Long, BigDecimal> percentageSplits;
        private Map<Long, Integer> sharesSplits;
    }

    // ================================================================
    // ENDPOINTS
    // ================================================================

    /**
     * Creates a new expense in a group.
     *
     * <p>The split type in the request body determines the calculation method:
     * <ul>
     *   <li>EQUAL: amount ÷ active members</li>
     *   <li>EXACT: explicit amounts per person</li>
     *   <li>PERCENTAGE: percentage per person (must sum to 100)</li>
     *   <li>SHARES: shares per person</li>
     * </ul>
     */
    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<ApiResponse<ExpenseDTO>> createExpense(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request) {

        ExpenseDTO expense;

        // Route to the appropriate service method based on split type.
        // Each method handles its own validation and calculation.
        switch (SplitType.valueOf(request.getSplitType().toUpperCase())) {
            case EQUAL:
                expense = expenseService.createEqualExpense(
                        groupId, request.getPaidByUserId(),
                        request.getDescription(), request.getAmount(),
                        request.getCurrency(), request.getExpenseDate());
                break;

            case EXACT:
                expense = expenseService.createExactExpense(
                        groupId, request.getPaidByUserId(),
                        request.getDescription(), request.getAmount(),
                        request.getCurrency(), request.getExpenseDate(),
                        request.getExactSplits());
                break;

            case PERCENTAGE:
                expense = expenseService.createPercentageExpense(
                        groupId, request.getPaidByUserId(),
                        request.getDescription(), request.getAmount(),
                        request.getCurrency(), request.getExpenseDate(),
                        request.getPercentageSplits());
                break;

            case SHARES:
                expense = expenseService.createSharesExpense(
                        groupId, request.getPaidByUserId(),
                        request.getDescription(), request.getAmount(),
                        request.getCurrency(), request.getExpenseDate(),
                        request.getSharesSplits());
                break;

            default:
                throw new com.spreetail.expenses.common.BusinessRuleException(
                        "Unsupported split type: " + request.getSplitType());
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(expense, "Expense created successfully"));
    }

    /**
     * Lists all expenses in a group (excluding settlements).
     */
    @GetMapping("/groups/{groupId}/expenses")
    public ResponseEntity<ApiResponse<List<ExpenseDTO>>> getExpensesByGroup(
            @PathVariable Long groupId) {
        List<ExpenseDTO> expenses = expenseService.getExpensesByGroupId(groupId);
        return ResponseEntity.ok(
                ApiResponse.success(expenses, "Expenses retrieved successfully")
        );
    }

    /**
     * Gets a single expense with all its splits.
     * Supports Rohan's drilldown requirement.
     */
    @GetMapping("/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<ExpenseDTO>> getExpense(
            @PathVariable Long expenseId) {
        ExpenseDTO expense = expenseService.getExpenseById(expenseId);
        return ResponseEntity.ok(
                ApiResponse.success(expense, "Expense retrieved successfully")
        );
    }
}
