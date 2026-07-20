package com.bank.application.service;

import com.bank.application.model.BalanceView;
import com.bank.application.model.MoneyMovementResult;
import com.bank.application.model.Page;
import com.bank.application.model.PageQuery;
import com.bank.application.model.TransactionHistoryEntry;
import com.bank.application.port.in.DepositUseCase;
import com.bank.application.port.in.DisburseCreditUseCase;
import com.bank.application.port.in.GetAccountStatementUseCase;
import com.bank.application.port.in.GetBalanceUseCase;
import com.bank.application.port.in.TransferUseCase;
import com.bank.application.port.in.WithdrawUseCase;
import com.bank.application.port.out.AccountRepository;
import com.bank.application.port.out.IdempotencyRepository;
import com.bank.application.port.out.LedgerRepository;
import com.bank.application.port.out.OperationLogRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.application.support.RequestHashing;
import com.bank.domain.LedgerDirection;
import com.bank.domain.Money;
import com.bank.domain.OperationType;
import com.bank.domain.exception.AccountNotActiveException;
import com.bank.domain.exception.ConcurrencyConflictException;
import com.bank.domain.exception.DuplicateIdempotencyKeyException;
import com.bank.domain.exception.IdempotencyConflictException;
import com.bank.domain.exception.InsufficientFundsException;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Account;
import com.bank.domain.model.IdempotencyRecord;
import com.bank.domain.model.LedgerEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * The core, framework-independent service for moving money. Every operation is a
 * single atomic transaction, uses {@link BigDecimal} money exclusively, is protected
 * against lost updates by optimistic locking with bounded retries, and is idempotent
 * on a client-supplied key.
 *
 * <p>This service depends only on ports: repositories ({@code port.out}) and a
 * {@link TransactionRunner} that owns transaction boundaries and translates the
 * persistence provider's failures into domain exceptions. As a result the core never
 * imports Spring Data, Spring's transaction API, or Spring/Hibernate exceptions.
 *
 * <h2>Why optimistic locking + retry (instead of pessimistic locks)?</h2>
 * Each account row carries a {@code @Version}. When two transactions read the same
 * account and both try to write it, the second commit fails; the runner surfaces this
 * as a domain {@link ConcurrencyConflictException}, which we retry on a fresh
 * transaction. This guarantees no lost updates while never holding a database row lock
 * across the transaction, keeping throughput high under the normal case of low
 * per-account contention. Under pathological contention on a single hot row the retries
 * effectively serialise the writers; the retry bound is configurable.
 */
@Service
public class MoneyMovementService
        implements DepositUseCase, WithdrawUseCase, TransferUseCase,
        DisburseCreditUseCase, GetBalanceUseCase, GetAccountStatementUseCase {

    /** Account number of the seeded system account that is the counter-leg for deposits/withdrawals. */
    public static final String EXTERNAL_CASH_ACCOUNT_NUMBER = "EXTERNAL-CASH";

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final OperationLogRepository operationLogRepository;
    private final TransactionRunner transactionRunner;
    private final ObjectMapper objectMapper;

    private final int maxRetries;
    private final long backoffBaseMillis;

    public MoneyMovementService(AccountRepository accountRepository,
                                LedgerRepository ledgerRepository,
                                IdempotencyRepository idempotencyRepository,
                                OperationLogRepository operationLogRepository,
                                TransactionRunner transactionRunner,
                                ObjectMapper objectMapper,
                                @Value("${banking.optimistic-lock.max-retries:20}") int maxRetries,
                                @Value("${banking.optimistic-lock.backoff-base-millis:10}") long backoffBaseMillis) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.operationLogRepository = operationLogRepository;
        this.transactionRunner = transactionRunner;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
        this.backoffBaseMillis = backoffBaseMillis;
    }

    // ------------------------------------------------------------------
    // Public operations
    // ------------------------------------------------------------------

    @Override
    public MoneyMovementResult deposit(Long accountId, BigDecimal amount, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String requestHash = RequestHashing.sha256Hex(OperationType.DEPOSIT, accountId, normalizedAmount);

        return runWithRetry(key, requestHash, () ->
                transactionRunner.inNewTransaction(() ->
                        doDeposit(accountId, normalizedAmount, key, requestHash)));
    }

    @Override
    public MoneyMovementResult withdraw(Long accountId, BigDecimal amount, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String requestHash = RequestHashing.sha256Hex(OperationType.WITHDRAWAL, accountId, normalizedAmount);

        return runWithRetry(key, requestHash, () ->
                transactionRunner.inNewTransaction(() ->
                        doWithdraw(accountId, normalizedAmount, key, requestHash)));
    }

    @Override
    public MoneyMovementResult transfer(Long sourceAccountId,
                                        Long destinationAccountId,
                                        BigDecimal amount,
                                        String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        BigDecimal normalizedAmount = normalizeAmount(amount);
        if (sourceAccountId == null || sourceAccountId.equals(destinationAccountId)) {
            throw new ValidationException("Source and destination accounts must be different");
        }
        String requestHash = RequestHashing.sha256Hex(
                OperationType.TRANSFER, sourceAccountId, destinationAccountId, normalizedAmount);

        return runWithRetry(key, requestHash, () ->
                transactionRunner.inNewTransaction(() ->
                        doTransfer(sourceAccountId, destinationAccountId, normalizedAmount, key, requestHash)));
    }

    @Override
    public MoneyMovementResult disburse(Long accountId, BigDecimal amount, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String requestHash = RequestHashing.sha256Hex(
                OperationType.CREDIT_DISBURSEMENT, accountId, normalizedAmount);

        return runWithRetry(key, requestHash, () ->
                transactionRunner.inNewTransaction(() ->
                        doCreditFromExternal(accountId, normalizedAmount, key, requestHash,
                                OperationType.CREDIT_DISBURSEMENT)));
    }

    @Override
    public BalanceView getBalance(Long accountId) {
        return transactionRunner.inReadOnlyTransaction(() -> {
            Account account = loadCustomerAccount(accountId);
            return new BalanceView(account.getId(), account.getCurrency(), account.getBalance());
        });
    }

    @Override
    public Page<TransactionHistoryEntry> getStatement(Long accountId, PageQuery query) {
        return transactionRunner.inReadOnlyTransaction(() -> {
            loadCustomerAccount(accountId);
            return ledgerRepository.findByAccount(accountId, query).map(MoneyMovementService::toHistoryEntry);
        });
    }

    /** The full statement as a list, newest first (used by service-level callers/tests). */
    public List<TransactionHistoryEntry> getTransactionHistory(Long accountId) {
        return transactionRunner.inReadOnlyTransaction(() -> {
            loadCustomerAccount(accountId);
            return ledgerRepository.findByAccountNewestFirst(accountId).stream()
                    .map(MoneyMovementService::toHistoryEntry)
                    .toList();
        });
    }

    // ------------------------------------------------------------------
    // Transactional work (one attempt each; called inside a transaction)
    // ------------------------------------------------------------------

    private MoneyMovementResult doDeposit(Long accountId, BigDecimal amount, String key, String requestHash) {
        return doCreditFromExternal(accountId, amount, key, requestHash, OperationType.DEPOSIT);
    }

    /**
     * Credit a customer account from the external cash counter-leg. Shared by deposits
     * and credit disbursements; the {@code type} distinguishes them in the ledger, the
     * audit log, and the idempotency record.
     */
    private MoneyMovementResult doCreditFromExternal(Long accountId, BigDecimal amount,
                                                     String key, String requestHash, OperationType type) {
        Optional<MoneyMovementResult> replay = findReplay(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account customer = requireActive(loadCustomerAccount(accountId));
        Account external = loadExternalCashAccount();

        UUID operationId = UUID.randomUUID();
        // Money flows in from outside -> credit the customer, debit external cash.
        postDoubleEntry(external, customer, amount, operationId, type);
        recordOperation(operationId, type, key, customer.getId(), external.getId(), amount);

        MoneyMovementResult result = MoneyMovementResult.singleAccount(
                operationId, type, amount, customerOperationTimestamp(),
                customer.getId(), customer.getBalance());
        persistIdempotency(key, type, requestHash, operationId, result);
        return result;
    }

    private MoneyMovementResult doWithdraw(Long accountId, BigDecimal amount, String key, String requestHash) {
        Optional<MoneyMovementResult> replay = findReplay(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account customer = requireActive(loadCustomerAccount(accountId));
        Account external = loadExternalCashAccount();
        requireSufficientFunds(customer, amount);

        UUID operationId = UUID.randomUUID();
        // Withdrawal: money flows out -> debit the customer, credit external cash.
        postDoubleEntry(customer, external, amount, operationId, OperationType.WITHDRAWAL);
        recordOperation(operationId, OperationType.WITHDRAWAL, key, customer.getId(), external.getId(), amount);

        MoneyMovementResult result = MoneyMovementResult.singleAccount(
                operationId, OperationType.WITHDRAWAL, amount, customerOperationTimestamp(),
                customer.getId(), customer.getBalance());
        persistIdempotency(key, OperationType.WITHDRAWAL, requestHash, operationId, result);
        return result;
    }

    private MoneyMovementResult doTransfer(Long sourceId, Long destinationId,
                                           BigDecimal amount, String key, String requestHash) {
        Optional<MoneyMovementResult> replay = findReplay(key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account source = requireActive(loadCustomerAccount(sourceId));
        Account destination = requireActive(loadCustomerAccount(destinationId));
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ValidationException("Cannot transfer between accounts of different currencies");
        }
        requireSufficientFunds(source, amount);

        UUID operationId = UUID.randomUUID();
        // Transfer: debit the source, credit the destination.
        postDoubleEntry(source, destination, amount, operationId, OperationType.TRANSFER);
        recordOperation(operationId, OperationType.TRANSFER, key, source.getId(), destination.getId(), amount);

        MoneyMovementResult result = MoneyMovementResult.transfer(
                operationId, amount, customerOperationTimestamp(),
                source.getId(), source.getBalance(),
                destination.getId(), destination.getBalance());
        persistIdempotency(key, OperationType.TRANSFER, requestHash, operationId, result);
        return result;
    }

    /**
     * Append the two immutable ledger legs and update both cached balances inside the
     * current transaction. The debit and credit are always written together, so the
     * ledger can never hold a half-finished movement.
     */
    private void postDoubleEntry(Account debitAccount, Account creditAccount,
                                 BigDecimal amount, UUID operationId, OperationType type) {
        debitAccount.applyDebit(amount);
        ledgerRepository.save(new LedgerEntry(
                debitAccount, operationId, type, LedgerDirection.DEBIT, amount, debitAccount.getBalance()));

        creditAccount.applyCredit(amount);
        ledgerRepository.save(new LedgerEntry(
                creditAccount, operationId, type, LedgerDirection.CREDIT, amount, creditAccount.getBalance()));
        // Cached balances on the managed entities are flushed (with @Version checks) on commit.
    }

    /**
     * Append the single, immutable operation-log audit row for this movement, in the
     * same transaction as the ledger legs (so the audit trail can never diverge from
     * the money that actually moved).
     */
    private void recordOperation(UUID operationId, OperationType type, String idempotencyKey,
                                 Long primaryAccountId, Long counterAccountId, BigDecimal amount) {
        operationLogRepository.save(new com.bank.domain.model.OperationLogEntry(
                operationId, type, idempotencyKey, primaryAccountId, counterAccountId, amount));
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    private Optional<MoneyMovementResult> findReplay(String key, String requestHash) {
        return idempotencyRepository.findByKey(key)
                .map(record -> deserializeReplay(key, requestHash, record));
    }

    private MoneyMovementResult deserializeReplay(String key, String requestHash, IdempotencyRecord record) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(key);
        }
        try {
            return objectMapper.readValue(record.getResponsePayload(), MoneyMovementResult.class).asReplay();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency payload for key " + key, e);
        }
    }

    private void persistIdempotency(String key, OperationType type, String requestHash,
                                    UUID operationId, MoneyMovementResult result) {
        try {
            String payload = objectMapper.writeValueAsString(result);
            idempotencyRepository.save(new IdempotencyRecord(key, type, requestHash, operationId, payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise idempotency payload for key " + key, e);
        }
    }

    // ------------------------------------------------------------------
    // Retry orchestration
    // ------------------------------------------------------------------

    private MoneyMovementResult runWithRetry(String key, String requestHash,
                                             Supplier<MoneyMovementResult> attempt) {
        int attemptNumber = 0;
        while (true) {
            attemptNumber++;
            try {
                return attempt.get();
            } catch (ConcurrencyConflictException e) {
                // Another transaction changed an account first; retry on a fresh transaction.
                if (attemptNumber > maxRetries) {
                    throw new ConcurrencyConflictException(
                            "Gave up after " + maxRetries + " optimistic-lock retries");
                }
                backoff(attemptNumber);
            } catch (DuplicateIdempotencyKeyException e) {
                // A concurrent request using the SAME idempotency key committed first.
                // Its money movement stands; return that stored result instead of ours.
                return findCommittedReplay(key, requestHash);
            }
        }
    }

    /** Re-read a competitor's committed idempotency result in a fresh read-only transaction. */
    private MoneyMovementResult findCommittedReplay(String key, String requestHash) {
        MoneyMovementResult replay = transactionRunner.inReadOnlyTransaction(() ->
                idempotencyRepository.findByKey(key)
                        .map(record -> deserializeReplay(key, requestHash, record))
                        .orElse(null));
        if (replay == null) {
            // Should not happen: the unique violation proves a committed row exists.
            throw new IllegalStateException("Idempotency key " + key + " collided but no record was found");
        }
        return replay;
    }

    private void backoff(int attemptNumber) {
        if (backoffBaseMillis <= 0) {
            return;
        }
        // Linear backoff with jitter to spread out contending retries.
        long jitter = ThreadLocalRandom.current().nextLong(backoffBaseMillis + 1);
        long sleepMillis = (long) backoffBaseMillis * attemptNumber + jitter;
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off before retry", interrupted);
        }
    }

    // ------------------------------------------------------------------
    // Loading / validation helpers
    // ------------------------------------------------------------------

    /** Loads a customer-facing account, hiding internal system accounts as "not found". */
    private Account loadCustomerAccount(Long accountId) {
        if (accountId == null) {
            throw new ValidationException("accountId must not be null");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> NotFoundException.account(accountId));
        if (account.isSystemAccount()) {
            throw NotFoundException.account(accountId);
        }
        return account;
    }

    private Account loadExternalCashAccount() {
        return accountRepository.findByAccountNumber(EXTERNAL_CASH_ACCOUNT_NUMBER)
                .orElseThrow(() -> new IllegalStateException(
                        "System account " + EXTERNAL_CASH_ACCOUNT_NUMBER + " is missing"));
    }

    private static Account requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
        return account;
    }

    private static void requireSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getId(), account.getBalance(), amount);
        }
    }

    private static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("idempotencyKey must not be blank");
        }
        String trimmed = idempotencyKey.strip();
        if (trimmed.length() > 80) {
            throw new ValidationException("idempotencyKey must be at most 80 characters");
        }
        return trimmed;
    }

    /**
     * Validates and canonicalises a client-supplied amount. Rejects null, non-positive
     * values, and amounts carrying more than two decimal places (so client money is
     * never silently rounded).
     */
    static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("amount must not be null");
        }
        if (!Money.isPositive(amount)) {
            throw new ValidationException("amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > Money.SCALE) {
            throw new ValidationException("amount must not have more than " + Money.SCALE + " decimal places");
        }
        return Money.normalize(amount);
    }

    private static Instant customerOperationTimestamp() {
        return Instant.now();
    }

    private static TransactionHistoryEntry toHistoryEntry(LedgerEntry entry) {
        return new TransactionHistoryEntry(
                entry.getId(),
                entry.getOperationId(),
                entry.getOperationType(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getCreatedAt());
    }
}
