package com.spreetail.expenses.settlement;

import com.spreetail.expenses.common.BusinessRuleException;
import com.spreetail.expenses.common.ResourceNotFoundException;
import com.spreetail.expenses.group.Group;
import com.spreetail.expenses.group.GroupRepository;
import com.spreetail.expenses.user.User;
import com.spreetail.expenses.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link PaymentService}.
 *
 * <p>Handles recording settlement payments between users.
 * Payments REDUCE outstanding balances — they are not expenses.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Cannot pay yourself (checked in code + DB constraint)</li>
 *   <li>Amount must be positive (checked in code + DB constraint)</li>
 *   <li>Both users must exist in the system</li>
 *   <li>Group must exist</li>
 * </ul>
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               GroupRepository groupRepository,
                               UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /**
     * Records a settlement payment.
     *
     * <p>@Transactional ensures atomicity — if validation passes
     * but the DB write fails, nothing is persisted.
     * Default rollback: any RuntimeException triggers rollback.
     *
     * @param groupId      the group context
     * @param paidByUserId who is paying (debtor)
     * @param paidToUserId who is receiving (creditor)
     * @param amount       payment amount in INR
     * @param paymentDate  when payment was made
     * @param notes        optional description
     * @return the saved payment DTO
     */
    @Override
    @Transactional
    public PaymentDTO recordPayment(Long groupId, Long paidByUserId, Long paidToUserId,
                                     BigDecimal amount, LocalDate paymentDate, String notes) {
        logger.info("Recording payment: {} → {} ₹{} in group {}",
                paidByUserId, paidToUserId, amount, groupId);

        // Validate: cannot pay yourself
        if (paidByUserId.equals(paidToUserId)) {
            throw new BusinessRuleException("Cannot record a payment to yourself");
        }

        // Validate: amount must be positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be positive, got: " + amount);
        }

        // Resolve entities
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        User paidBy = userRepository.findById(paidByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", paidByUserId));
        User paidTo = userRepository.findById(paidToUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", paidToUserId));

        Payment payment = Payment.builder()
                .group(group)
                .paidBy(paidBy)
                .paidTo(paidTo)
                .amount(amount)
                .paymentDate(paymentDate)
                .notes(notes)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        logger.info("Payment recorded: ID={}, {} → {} ₹{}",
                savedPayment.getId(), paidBy.getFullName(), paidTo.getFullName(), amount);

        return PaymentDTO.fromEntity(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByGroupId(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        return paymentRepository.findByGroupId(groupId)
                .stream()
                .map(PaymentDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
