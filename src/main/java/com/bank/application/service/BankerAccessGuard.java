package com.bank.application.service;

import com.bank.application.port.out.BankerAssignmentRepository;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.model.CustomerBankerAssignment;
import org.springframework.stereotype.Component;

/**
 * Authorizes that a banker may act on a given customer's credit applications — i.e. that
 * the customer is assigned to that banker.
 *
 * <p>Mirroring {@link AccountAccessGuard}, an unauthorized access is reported as
 * "not found" (via the calling application id) rather than "forbidden", so a banker
 * cannot probe which application ids exist for customers that are not theirs.
 */
@Component
public class BankerAccessGuard {

    private final BankerAssignmentRepository assignmentRepository;

    public BankerAccessGuard(BankerAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * @throws NotFoundException (for {@code applicationId}) unless {@code bankerId} is the
     *         banker assigned to {@code customerId}.
     */
    public void requireManages(Long customerId, Long bankerId, Long applicationId) {
        Long assigned = assignmentRepository.findByCustomerId(customerId)
                .map(CustomerBankerAssignment::getBankerId)
                .orElse(null);
        if (assigned == null || !assigned.equals(bankerId)) {
            throw NotFoundException.creditApplication(applicationId);
        }
    }
}
