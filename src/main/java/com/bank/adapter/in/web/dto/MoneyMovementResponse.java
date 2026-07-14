package com.bank.adapter.in.web.dto;

import com.bank.application.model.MoneyMovementResult;
import com.bank.domain.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of a deposit, withdrawal or transfer.
 *
 * <p>For a deposit/withdrawal, {@code primaryAccountId}/{@code primaryBalance}
 * describe the affected account and the counter fields are null. For a transfer,
 * the primary fields describe the source and the counter fields the destination.
 *
 * @param replayed true when this response was served from the idempotency store
 *                 because the same idempotency key was seen before
 */
public record MoneyMovementResponse(
        UUID operationId,
        OperationType type,
        BigDecimal amount,
        Instant timestamp,
        Long primaryAccountId,
        BigDecimal primaryBalance,
        Long counterAccountId,
        BigDecimal counterBalance,
        boolean replayed) {

    public static MoneyMovementResponse from(MoneyMovementResult result) {
        return new MoneyMovementResponse(
                result.operationId(),
                result.type(),
                result.amount(),
                result.timestamp(),
                result.primaryAccountId(),
                result.primaryBalance(),
                result.counterAccountId(),
                result.counterBalance(),
                result.replayed());
    }
}
