package com.bank.application.port.out;

import com.bank.domain.model.OperationLogEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port for the audit/operation log (implemented by a persistence adapter). */
public interface OperationLogRepository {

    OperationLogEntry save(OperationLogEntry entry);

    Optional<OperationLogEntry> findByOperationId(UUID operationId);

    long countByPrimaryAccountId(Long primaryAccountId);

    /** The most recent audit entries, newest first (admin log view), capped at {@code limit}. */
    List<OperationLogEntry> findRecent(int limit);
}
