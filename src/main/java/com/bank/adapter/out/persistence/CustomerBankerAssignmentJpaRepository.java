package com.bank.adapter.out.persistence;

import com.bank.domain.model.CustomerBankerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA repository for customer&ndash;banker assignments (an outbound-adapter detail). */
public interface CustomerBankerAssignmentJpaRepository
        extends JpaRepository<CustomerBankerAssignment, Long> {

    Optional<CustomerBankerAssignment> findByCustomerId(Long customerId);
}
