package com.spreetail.expenses.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for payment/settlement operations.
 *
 * @see PaymentServiceImpl
 */
public interface PaymentService {

    /**
     * Records a settlement payment from one user to another.
     *
     * @param groupId     the group this payment belongs to
     * @param paidByUserId the user making the payment (debtor)
     * @param paidToUserId the user receiving the payment (creditor)
     * @param amount       payment amount in INR (must be positive)
     * @param paymentDate  when the payment was made
     * @param notes        optional notes
     * @return the created payment DTO
     */
    PaymentDTO recordPayment(Long groupId, Long paidByUserId, Long paidToUserId,
                              BigDecimal amount, LocalDate paymentDate, String notes);

    /**
     * Retrieves all payments in a group.
     */
    List<PaymentDTO> getPaymentsByGroupId(Long groupId);
}
