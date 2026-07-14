package com.bank.application.port.out;

import com.bank.domain.model.Account;

import java.util.List;
import java.util.Optional;

/** Output port for account persistence (implemented by a persistence adapter). */
public interface AccountRepository {

    Optional<Account> findById(Long id);

    /** All accounts owned by the given customer (system accounts have no owner and are excluded). */
    List<Account> findByCustomerId(Long customerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /** True only when the account exists and is owned by the given customer. */
    boolean existsByIdAndCustomerId(Long id, Long customerId);

    Account save(Account account);
}
