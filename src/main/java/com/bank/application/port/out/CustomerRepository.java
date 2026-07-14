package com.bank.application.port.out;

import com.bank.domain.CustomerRole;
import com.bank.domain.model.Customer;

import java.util.List;
import java.util.Optional;

/** Output port for customer persistence (implemented by a persistence adapter). */
public interface CustomerRepository {

    Optional<Customer> findById(Long id);

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Ids of all customers holding the given role (e.g. every BANKER, for assignment). */
    List<Long> findIdsByRole(CustomerRole role);

    Customer save(Customer customer);
}
