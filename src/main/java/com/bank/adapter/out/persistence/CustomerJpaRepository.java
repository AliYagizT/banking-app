package com.bank.adapter.out.persistence;

import com.bank.domain.CustomerRole;
import com.bank.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA repository for customers (an outbound-adapter detail). */
public interface CustomerJpaRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select c.id from Customer c where c.role = :role")
    List<Long> findIdsByRole(@Param("role") CustomerRole role);

    List<Customer> findAllByOrderByIdDesc();
}
