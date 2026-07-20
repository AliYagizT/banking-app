package com.bank.adapter.in.web.dto;

import com.bank.domain.OperationType;
import com.bank.domain.model.OperationLogEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One audit-log row as shown in the admin console. */
public record OperationLogResponse(
        Long id,
        UUID operationId,
        OperationType type,
        Long primaryAccountId,
        Long counterAccountId,
        BigDecimal amount,
        String idempotencyKey,
        Instant createdAt) {

    public static OperationLogResponse from(OperationLogEntry e) {
        return new OperationLogResponse(
                e.getId(),
                e.getOperationId(),
                e.getOperationType(),
                e.getPrimaryAccountId(),
                e.getCounterAccountId(),
                e.getAmount(),
                e.getIdempotencyKey(),
                e.getCreatedAt());
    }
}
