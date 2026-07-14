package com.bank.application.port.out;

import com.bank.domain.model.Customer;

import java.util.Optional;

/** Output port for customer persistence (implemented by a persistence adapter). */
public interface CustomerRepository {

    Optional<Customer> findById(Long id);

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    Customer save(Customer customer);
}
