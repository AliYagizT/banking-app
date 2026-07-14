package com.bank.adapter.in.web.dto;

import com.bank.application.model.TransactionHistoryEntry;
import com.bank.domain.LedgerDirection;
import com.bank.domain.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One statement line, derived from an immutable ledger entry. */
public record TransactionHistoryItem(
        Long entryId,
        UUID operationId,
        OperationType type,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant timestamp) {

    public static TransactionHistoryItem from(TransactionHistoryEntry entry) {
        return new TransactionHistoryItem(
                entry.entryId(),
                entry.operationId(),
                entry.type(),
                entry.direction(),
                entry.amount(),
                entry.balanceAfter(),
                entry.timestamp());
    }
}
