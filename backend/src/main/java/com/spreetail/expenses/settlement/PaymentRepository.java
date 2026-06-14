package com.spreetail.expenses.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Finds all payments within a group.
     * Used by BalanceCalculationService (RULE 4: settlement deduction)
     * and the settlement history view.
     */
    List<Payment> findByGroupId(Long groupId);

    /**
     * Finds all payments made by a specific user in a group.
     */
    List<Payment> findByGroupIdAndPaidById(Long groupId, Long paidById);

    /**
     * Finds all payments received by a specific user in a group.
     */
    List<Payment> findByGroupIdAndPaidToId(Long groupId, Long paidToId);
}
