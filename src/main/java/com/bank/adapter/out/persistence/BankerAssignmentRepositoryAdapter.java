package com.bank.adapter.out.persistence;

import com.bank.application.port.out.BankerAssignmentRepository;
import com.bank.domain.model.CustomerBankerAssignment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapts {@link CustomerBankerAssignmentJpaRepository} to the {@link BankerAssignmentRepository} port. */
@Component
public class BankerAssignmentRepositoryAdapter implements BankerAssignmentRepository {

    private final CustomerBankerAssignmentJpaRepository jpa;

    public BankerAssignmentRepositoryAdapter(CustomerBankerAssignmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CustomerBankerAssignment save(CustomerBankerAssignment assignment) {
        return jpa.save(assignment);
    }

    @Override
    public Optional<CustomerBankerAssignment> findByCustomerId(Long customerId) {
        return jpa.findByCustomerId(customerId);
    }
}
