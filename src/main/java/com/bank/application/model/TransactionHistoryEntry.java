package com.bank.application.model;

import com.bank.domain.LedgerDirection;
import com.bank.domain.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of an account's statement, derived directly from an immutable ledger
 * entry. {@code direction} is from the account's own perspective (CREDIT = money
 * in, DEBIT = money out) and {@code balanceAfter} is the account balance right
 * after the entry was applied.
 */
public record TransactionHistoryEntry(
        Long entryId,
        UUID operationId,
        OperationType type,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant timestamp) {
}
