package com.bank.adapter.out.persistence;

import com.bank.application.port.out.CustomerRepository;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.Customer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the {@link CustomerJpaRepository} to the {@link CustomerRepository} output port. */
@Component
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpa;

    public CustomerRepositoryAdapter(CustomerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Customer> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public List<Long> findIdsByRole(CustomerRole role) {
        return jpa.findIdsByRole(role);
    }

    @Override
    public List<Customer> findAllNewestFirst() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Customer save(Customer customer) {
        return jpa.save(customer);
    }
}
