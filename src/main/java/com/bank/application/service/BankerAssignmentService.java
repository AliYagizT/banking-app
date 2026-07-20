package com.bank.application.service;

import com.bank.application.port.out.BankerAssignmentRepository;
import com.bank.application.port.out.CustomerRepository;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.CustomerBankerAssignment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assigns each customer a relationship banker who will evaluate their credit
 * applications. Selection is random across all BANKER-role users, which spreads load
 * evenly without any central scheduling. One assignment per customer; it is created at
 * registration and re-attempted lazily at application time if it is still missing (e.g.
 * the customer registered before any banker existed).
 *
 * <p>These methods do not open transactions of their own; callers invoke them inside an
 * existing transaction so the assignment row commits atomically with the surrounding work.
 */
@Component
public class BankerAssignmentService {

    private final BankerAssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;

    public BankerAssignmentService(BankerAssignmentRepository assignmentRepository,
                                   CustomerRepository customerRepository) {
        this.assignmentRepository = assignmentRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * The customer's assigned banker id, assigning a random one now if none exists yet.
     * Empty only when the bank has no bankers at all.
     */
    public Optional<Long> getOrAssignBanker(Long customerId) {
        Optional<Long> existing = currentBanker(customerId);
        if (existing.isPresent()) {
            return existing;
        }
        List<Long> bankerIds = customerRepository.findIdsByRole(CustomerRole.BANKER);
        if (bankerIds.isEmpty()) {
            return Optional.empty();
        }
        Long chosen = bankerIds.get(ThreadLocalRandom.current().nextInt(bankerIds.size()));
        assignmentRepository.save(new CustomerBankerAssignment(customerId, chosen));
        return Optional.of(chosen);
    }

    /** The customer's currently assigned banker id, if any. */
    public Optional<Long> currentBanker(Long customerId) {
        return assignmentRepository.findByCustomerId(customerId)
                .map(CustomerBankerAssignment::getBankerId);
    }
}
