package com.bank.adapter.out.persistence;

import com.bank.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA repository for accounts (an outbound-adapter detail). */
public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerId(Long customerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByIdAndCustomerId(Long id, Long customerId);
}
