package com.bank.persistence;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.AccountJpaRepository;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.domain.AccountStatus;
import com.bank.domain.AccountType;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1 end-to-end persistence check: create a customer and an account against a
 * real PostgreSQL (via Flyway migrations) and read them back.
 */
class CustomerAndAccountPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountJpaRepository accountRepository;

    @Test
    void createsCustomerAndAccountEndToEnd() {
        Customer customer = customerService.register("Ada Lovelace", "ada@example.com");

        assertThat(customer.getId()).isNotNull();
        assertThat(customer.getCreatedAt()).isNotNull();
        assertThat(customer.isActive()).isTrue();

        Account account = accountService.openAccount(customer.getId(), "USD");

        assertThat(account.getId()).isNotNull();
        assertThat(account.getAccountNumber()).hasSize(16);
        assertThat(account.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(account.getAccountType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        // Cached balance is stored at scale 2.
        assertThat(account.getBalance().scale()).isEqualTo(2);

        Account reloaded = accountService.getAccount(account.getId());
        assertThat(reloaded.getAccountNumber()).isEqualTo(account.getAccountNumber());
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() {
        customerService.register("First", "dup@example.com");

        assertThatThrownBy(() -> customerService.register("Second", "DUP@example.com"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        Customer customer = customerService.register("Euro Fan", "euro@example.com");

        assertThatThrownBy(() -> accountService.openAccount(customer.getId(), "EUR"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported currency");
    }

    @Test
    void systemExternalCashAccountIsSeeded() {
        Account external = accountRepository.findByAccountNumber("EXTERNAL-CASH").orElseThrow();

        assertThat(external.getAccountType()).isEqualTo(AccountType.SYSTEM);
        assertThat(external.isSystemAccount()).isTrue();
        assertThat(external.getCustomer()).isNull();
    }
}
