package com.bank.application.service;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.AccountJpaRepository;
import com.bank.adapter.out.persistence.OperationLogJpaRepository;
import com.bank.application.model.MoneyMovementResult;
import com.bank.domain.OperationType;
import com.bank.domain.model.Account;
import com.bank.domain.model.OperationLogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: the dedicated operation/audit log records exactly one immutable row per
 * executed money movement, and idempotent replays never append a second row.
 */
class OperationAuditIT extends AbstractIntegrationTest {

    @Autowired
    private MoneyMovementService moneyMovementService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountJpaRepository accountRepository;
    @Autowired
    private OperationLogJpaRepository operationLogRepository;

    private Account newAccount() {
        var customer = customerService.register(
                "Audit User", "audit-" + UUID.randomUUID() + "@example.com");
        return accountService.openAccount(customer.getId(), "USD");
    }

    private Long externalAccountId() {
        return accountRepository.findByAccountNumber(MoneyMovementService.EXTERNAL_CASH_ACCOUNT_NUMBER)
                .orElseThrow().getId();
    }

    @Test
    void depositWritesOneAuditRowAgainstExternalCash() {
        Account account = newAccount();

        MoneyMovementResult result = moneyMovementService.deposit(
                account.getId(), new BigDecimal("75.00"), UUID.randomUUID().toString());

        OperationLogEntry entry = operationLogRepository.findByOperationId(result.operationId()).orElseThrow();
        assertThat(entry.getOperationType()).isEqualTo(OperationType.DEPOSIT);
        assertThat(entry.getAmount()).isEqualByComparingTo("75.00");
        assertThat(entry.getPrimaryAccountId()).isEqualTo(account.getId());
        assertThat(entry.getCounterAccountId()).isEqualTo(externalAccountId());
        assertThat(entry.getCreatedAt()).isNotNull();
        assertThat(operationLogRepository.countByPrimaryAccountId(account.getId())).isEqualTo(1);
    }

    @Test
    void transferWritesAuditRowWithSourceAndDestination() {
        Account source = newAccount();
        moneyMovementService.deposit(source.getId(), new BigDecimal("100.00"), UUID.randomUUID().toString());
        Account destination = newAccount();

        MoneyMovementResult result = moneyMovementService.transfer(
                source.getId(), destination.getId(), new BigDecimal("40.00"), UUID.randomUUID().toString());

        OperationLogEntry entry = operationLogRepository.findByOperationId(result.operationId()).orElseThrow();
        assertThat(entry.getOperationType()).isEqualTo(OperationType.TRANSFER);
        assertThat(entry.getPrimaryAccountId()).isEqualTo(source.getId());
        assertThat(entry.getCounterAccountId()).isEqualTo(destination.getId());
        assertThat(entry.getAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void replayingIdempotencyKeyDoesNotWriteSecondAuditRow() {
        Account account = newAccount();
        String key = UUID.randomUUID().toString();

        moneyMovementService.deposit(account.getId(), new BigDecimal("20.00"), key);
        moneyMovementService.deposit(account.getId(), new BigDecimal("20.00"), key); // replay

        assertThat(operationLogRepository.countByPrimaryAccountId(account.getId())).isEqualTo(1);
    }
}
