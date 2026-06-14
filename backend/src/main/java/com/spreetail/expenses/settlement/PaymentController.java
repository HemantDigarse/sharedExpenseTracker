package com.spreetail.expenses.settlement;

import com.spreetail.expenses.common.ApiResponse;
import jakarta.validation.Valid;
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

/**
 * REST controller for settlement/payment endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/groups/{groupId}/payments — record a settlement payment</li>
 *   <li>GET  /api/groups/{groupId}/payments — list all payments in a group</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/groups/{groupId}/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class RecordPaymentRequest {
        @NotNull(message = "Payer user ID is required")
        private Long paidByUserId;

        @NotNull(message = "Recipient user ID is required")
        private Long paidToUserId;

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        private BigDecimal amount;

        @NotNull(message = "Payment date is required")
        private LocalDate paymentDate;

        private String notes;
    }

    /**
     * Records a settlement payment between two group members.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentDTO>> recordPayment(
            @PathVariable Long groupId,
            @Valid @RequestBody RecordPaymentRequest request) {

        PaymentDTO payment = paymentService.recordPayment(
                groupId, request.getPaidByUserId(), request.getPaidToUserId(),
                request.getAmount(), request.getPaymentDate(), request.getNotes());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(payment, "Payment recorded successfully"));
    }

    /**
     * Lists all settlement payments in a group.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPayments(
            @PathVariable Long groupId) {
        List<PaymentDTO> payments = paymentService.getPaymentsByGroupId(groupId);
        return ResponseEntity.ok(
                ApiResponse.success(payments, "Payments retrieved successfully")
        );
    }
}
