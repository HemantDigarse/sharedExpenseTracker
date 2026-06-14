package com.spreetail.expenses.settlement;

import com.spreetail.expenses.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for Payment entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDTO {

    private Long id;
    private Long groupId;
    private UserDTO paidBy;
    private UserDTO paidTo;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String notes;
    private OffsetDateTime createdAt;

    /**
     * Converts a Payment entity to a PaymentDTO.
     */
    public static PaymentDTO fromEntity(Payment payment) {
        return PaymentDTO.builder()
                .id(payment.getId())
                .groupId(payment.getGroup().getId())
                .paidBy(UserDTO.fromEntity(payment.getPaidBy()))
                .paidTo(UserDTO.fromEntity(payment.getPaidTo()))
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
