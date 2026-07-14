package com.bank.application.model;

import com.bank.domain.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of a deposit, withdrawal or transfer. This is the value returned to
 * callers and the value persisted (as JSON) for idempotent replay.
 *
 * <p>For a deposit/withdrawal only the customer-facing ("primary") account is
 * reported; the internal counter account is intentionally not exposed. For a
 * transfer the primary account is the source and the counter account is the
 * destination.
 *
 * @param replayed false for the original execution, true when served from the
 *                 idempotency store for a repeated request
 */
public record MoneyMovementResult(
        UUID operationId,
        OperationType type,
        BigDecimal amount,
        Instant timestamp,
        Long primaryAccountId,
        BigDecimal primaryBalance,
        Long counterAccountId,
        BigDecimal counterBalance,
        boolean replayed) {

    public static MoneyMovementResult singleAccount(UUID operationId,
                                                    OperationType type,
                                                    BigDecimal amount,
                                                    Instant timestamp,
                                                    Long accountId,
                                                    BigDecimal balanceAfter) {
        return new MoneyMovementResult(operationId, type, amount, timestamp,
                accountId, balanceAfter, null, null, false);
    }

    public static MoneyMovementResult transfer(UUID operationId,
                                               BigDecimal amount,
                                               Instant timestamp,
                                               Long sourceAccountId,
                                               BigDecimal sourceBalance,
                                               Long destinationAccountId,
                                               BigDecimal destinationBalance) {
        return new MoneyMovementResult(operationId, OperationType.TRANSFER, amount, timestamp,
                sourceAccountId, sourceBalance, destinationAccountId, destinationBalance, false);
    }

    /** A copy flagged as having been served from the idempotency store. */
    public MoneyMovementResult asReplay() {
        return new MoneyMovementResult(operationId, type, amount, timestamp,
                primaryAccountId, primaryBalance, counterAccountId, counterBalance, true);
    }
}
