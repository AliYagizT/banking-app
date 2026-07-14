package com.bank.adapter.out.persistence;

import com.bank.application.port.out.AccountRepository;
import com.bank.domain.model.Account;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts the {@link AccountJpaRepository} to the {@link AccountRepository} output port. */
@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    public AccountRepositoryAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Account> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Account> findByCustomerId(Long customerId) {
        return jpa.findByCustomerId(customerId);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return jpa.findByAccountNumber(accountNumber);
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return jpa.existsByAccountNumber(accountNumber);
    }

    @Override
    public boolean existsByIdAndCustomerId(Long id, Long customerId) {
        return jpa.existsByIdAndCustomerId(id, customerId);
    }

    @Override
    public Account save(Account account) {
        return jpa.save(account);
    }
}
