package com.spreetail.expenses.expense;

import com.spreetail.expenses.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data Transfer Object for Expense entity.
 *
 * <p>Includes the expense details and all its splits.
 * Used in expense list views, detail views, and balance breakdown
 * (Rohan's drilldown requirement).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDTO {

    private Long id;
    private Long groupId;
    private UserDTO paidBy;
    private String description;
    private BigDecimal amount;
    private String currency;
    private BigDecimal amountInInr;
    private String splitType;
    private LocalDate expenseDate;
    private OffsetDateTime createdAt;
    private Boolean isSettlement;

    /**
     * Individual splits showing each person's share.
     * Enables Rohan's requirement: "Show exactly which expenses
     * make up my balance."
     */
    private List<SplitDTO> splits;

    /**
     * Nested DTO for each person's share of the expense.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitDTO {
        private Long id;
        private UserDTO user;
        private BigDecimal shareAmount;
        private BigDecimal sharePercentage;
        private Integer shareUnits;
        private BigDecimal finalAmountOwed;

        public static SplitDTO fromEntity(ExpenseSplit split) {
            return SplitDTO.builder()
                    .id(split.getId())
                    .user(UserDTO.fromEntity(split.getUser()))
                    .shareAmount(split.getShareAmount())
                    .sharePercentage(split.getSharePercentage())
                    .shareUnits(split.getShareUnits())
                    .finalAmountOwed(split.getFinalAmountOwed())
                    .build();
        }
    }

    /**
     * Converts an Expense entity to an ExpenseDTO (without splits).
     */
    public static ExpenseDTO fromEntity(Expense expense) {
        return ExpenseDTO.builder()
                .id(expense.getId())
                .groupId(expense.getGroup().getId())
                .paidBy(UserDTO.fromEntity(expense.getPaidBy()))
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency().name())
                .amountInInr(expense.getAmountInInr())
                .splitType(expense.getSplitType().name())
                .expenseDate(expense.getExpenseDate())
                .createdAt(expense.getCreatedAt())
                .isSettlement(expense.getIsSettlement())
                .build();
    }

    /**
     * Converts an Expense entity with its splits to a full ExpenseDTO.
     */
    public static ExpenseDTO fromEntityWithSplits(Expense expense) {
        ExpenseDTO dto = fromEntity(expense);
        dto.setSplits(
                expense.getSplits().stream()
                        .map(SplitDTO::fromEntity)
                        .toList()
        );
        return dto;
    }
}
