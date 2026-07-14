package com.bank.application.service;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.AccountJpaRepository;
import com.bank.adapter.out.persistence.LedgerEntryJpaRepository;
import com.bank.application.model.MoneyMovementResult;
import com.bank.application.model.TransactionHistoryEntry;
import com.bank.domain.LedgerDirection;
import com.bank.domain.OperationType;
import com.bank.domain.exception.AccountNotActiveException;
import com.bank.domain.exception.IdempotencyConflictException;
import com.bank.domain.exception.InsufficientFundsException;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyMovementIT extends AbstractIntegrationTest {

    @Autowired
    private MoneyMovementService moneyMovementService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountJpaRepository accountRepository;
    @Autowired
    private LedgerEntryJpaRepository ledgerEntryRepository;

    // ---- helpers -------------------------------------------------------

    private Account newAccount() {
        var customer = customerService.register(
                "Test User", "user-" + UUID.randomUUID() + "@example.com", "password123");
        return accountService.openAccount(customer.getId(), "USD");
    }

    private Account newFundedAccount(String amount) {
        Account account = newAccount();
        moneyMovementService.deposit(account.getId(), new BigDecimal(amount), UUID.randomUUID().toString());
        return account;
    }

    private BigDecimal balanceOf(Long accountId) {
        return moneyMovementService.getBalance(accountId).balance();
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    // ---- deposit -------------------------------------------------------

    @Test
    void depositIncreasesBalanceAndWritesBalancedDoubleEntry() {
        Account account = newAccount();

        MoneyMovementResult result = moneyMovementService.deposit(account.getId(), new BigDecimal("100.00"), key());

        assertThat(result.type()).isEqualTo(OperationType.DEPOSIT);
        assertThat(result.replayed()).isFalse();
        assertThat(result.primaryBalance()).isEqualByComparingTo("100.00");
        assertThat(balanceOf(account.getId())).isEqualByComparingTo("100.00");

        // Exactly one ledger leg lands on the customer account, and it is a CREDIT.
        var entries = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getDirection()).isEqualTo(LedgerDirection.CREDIT);
        // The operation's two legs (customer + external) net to zero.
        assertThat(totalSignedForOperation(result.operationId())).isEqualByComparingTo("0.00");
    }

    // ---- withdraw ------------------------------------------------------

    @Test
    void withdrawDecreasesBalance() {
        Account account = newFundedAccount("100.00");

        MoneyMovementResult result = moneyMovementService.withdraw(account.getId(), new BigDecimal("30.00"), key());

        assertThat(result.type()).isEqualTo(OperationType.WITHDRAWAL);
        assertThat(balanceOf(account.getId())).isEqualByComparingTo("70.00");
        assertThat(totalSignedForOperation(result.operationId())).isEqualByComparingTo("0.00");
    }

    @Test
    void withdrawRejectedOnInsufficientFunds() {
        Account account = newFundedAccount("20.00");

        assertThatThrownBy(() -> moneyMovementService.withdraw(account.getId(), new BigDecimal("50.00"), key()))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(account.getId())).isEqualByComparingTo("20.00");
    }

    // ---- transfer ------------------------------------------------------

    @Test
    void transferMovesMoneyAtomically() {
        Account source = newFundedAccount("100.00");
        Account destination = newAccount();

        MoneyMovementResult result = moneyMovementService.transfer(
                source.getId(), destination.getId(), new BigDecimal("40.00"), key());

        assertThat(result.primaryAccountId()).isEqualTo(source.getId());
        assertThat(result.primaryBalance()).isEqualByComparingTo("60.00");
        assertThat(result.counterAccountId()).isEqualTo(destination.getId());
        assertThat(result.counterBalance()).isEqualByComparingTo("40.00");

        assertThat(balanceOf(source.getId())).isEqualByComparingTo("60.00");
        assertThat(balanceOf(destination.getId())).isEqualByComparingTo("40.00");
        assertThat(totalSignedForOperation(result.operationId())).isEqualByComparingTo("0.00");
    }

    @Test
    void transferRejectsSameSourceAndDestination() {
        Account account = newFundedAccount("100.00");

        assertThatThrownBy(() -> moneyMovementService.transfer(
                account.getId(), account.getId(), new BigDecimal("10.00"), key()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different");
    }

    @Test
    void transferRejectedOnInsufficientFundsLeavesBothBalancesUnchanged() {
        Account source = newFundedAccount("10.00");
        Account destination = newFundedAccount("5.00");

        assertThatThrownBy(() -> moneyMovementService.transfer(
                source.getId(), destination.getId(), new BigDecimal("50.00"), key()))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(source.getId())).isEqualByComparingTo("10.00");
        assertThat(balanceOf(destination.getId())).isEqualByComparingTo("5.00");
    }

    // ---- validation ----------------------------------------------------

    @Test
    void rejectsNonPositiveAmount() {
        Account account = newAccount();
        assertThatThrownBy(() -> moneyMovementService.deposit(account.getId(), new BigDecimal("0.00"), key()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> moneyMovementService.deposit(account.getId(), new BigDecimal("-5.00"), key()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsUnknownAccount() {
        assertThatThrownBy(() -> moneyMovementService.deposit(999_999_999L, new BigDecimal("10.00"), key()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotTargetSystemAccount() {
        Long externalId = accountRepository.findByAccountNumber(
                MoneyMovementService.EXTERNAL_CASH_ACCOUNT_NUMBER).orElseThrow().getId();

        // The system account must be invisible to client operations.
        assertThatThrownBy(() -> moneyMovementService.deposit(externalId, new BigDecimal("10.00"), key()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> moneyMovementService.getBalance(externalId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsOperationsOnFrozenAccount() {
        Account account = newFundedAccount("100.00");
        Account stored = accountRepository.findById(account.getId()).orElseThrow();
        stored.setStatus(com.bank.domain.AccountStatus.FROZEN);
        accountRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> moneyMovementService.withdraw(account.getId(), new BigDecimal("10.00"), key()))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // ---- idempotency ---------------------------------------------------

    @Test
    void replayingSameKeyDoesNotMoveMoneyTwice() {
        Account account = newAccount();
        String idempotencyKey = key();

        MoneyMovementResult first = moneyMovementService.deposit(account.getId(), new BigDecimal("50.00"), idempotencyKey);
        MoneyMovementResult second = moneyMovementService.deposit(account.getId(), new BigDecimal("50.00"), idempotencyKey);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        // Same logical operation: identical operation id and amount.
        assertThat(second.operationId()).isEqualTo(first.operationId());
        // Money moved exactly once.
        assertThat(balanceOf(account.getId())).isEqualByComparingTo("50.00");
        assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId())).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        Account account = newAccount();
        String idempotencyKey = key();

        moneyMovementService.deposit(account.getId(), new BigDecimal("50.00"), idempotencyKey);

        assertThatThrownBy(() -> moneyMovementService.deposit(account.getId(), new BigDecimal("60.00"), idempotencyKey))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        Account account = newAccount();
        assertThatThrownBy(() -> moneyMovementService.deposit(account.getId(), new BigDecimal("10.00"), "  "))
                .isInstanceOf(ValidationException.class);
    }

    // ---- ledger consistency & history ---------------------------------

    @Test
    void cachedBalanceAlwaysMatchesLedgerSum() {
        Account account = newAccount();
        moneyMovementService.deposit(account.getId(), new BigDecimal("100.00"), key());
        moneyMovementService.withdraw(account.getId(), new BigDecimal("30.00"), key());
        moneyMovementService.deposit(account.getId(), new BigDecimal("5.50"), key());

        BigDecimal cached = balanceOf(account.getId());
        BigDecimal fromLedger = ledgerEntryRepository.sumSignedAmountByAccountId(account.getId());

        assertThat(cached).isEqualByComparingTo("75.50");
        assertThat(cached).isEqualByComparingTo(fromLedger);
    }

    @Test
    void historyIsReturnedNewestFirst() {
        Account account = newAccount();
        moneyMovementService.deposit(account.getId(), new BigDecimal("10.00"), key());
        moneyMovementService.deposit(account.getId(), new BigDecimal("20.00"), key());
        moneyMovementService.withdraw(account.getId(), new BigDecimal("5.00"), key());

        List<TransactionHistoryEntry> history = moneyMovementService.getTransactionHistory(account.getId());

        assertThat(history).hasSize(3);
        // Newest first: the withdrawal is most recent.
        assertThat(history.get(0).type()).isEqualTo(OperationType.WITHDRAWAL);
        assertThat(history.get(0).direction()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(history.get(0).balanceAfter()).isEqualByComparingTo("25.00");
    }

    private BigDecimal totalSignedForOperation(UUID operationId) {
        return ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getOperationId().equals(operationId))
                .map(entry -> entry.getDirection() == LedgerDirection.CREDIT
                        ? entry.getAmount() : entry.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
