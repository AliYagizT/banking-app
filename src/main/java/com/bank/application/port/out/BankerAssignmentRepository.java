package com.bank.application.port.out;

import com.bank.domain.model.CustomerBankerAssignment;

import java.util.Optional;

/** Output port for customer&ndash;banker assignments. */
public interface BankerAssignmentRepository {

    CustomerBankerAssignment save(CustomerBankerAssignment assignment);

    Optional<CustomerBankerAssignment> findByCustomerId(Long customerId);
}
