package com.bank.adapter.out.persistence;

import com.bank.application.port.out.OperationLogRepository;
import com.bank.domain.model.OperationLogEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts the {@link OperationLogJpaRepository} to the {@link OperationLogRepository} output port. */
@Component
public class OperationLogRepositoryAdapter implements OperationLogRepository {

    private final OperationLogJpaRepository jpa;

    public OperationLogRepositoryAdapter(OperationLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OperationLogEntry save(OperationLogEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public Optional<OperationLogEntry> findByOperationId(UUID operationId) {
        return jpa.findByOperationId(operationId);
    }

    @Override
    public long countByPrimaryAccountId(Long primaryAccountId) {
        return jpa.countByPrimaryAccountId(primaryAccountId);
    }

    @Override
    public List<OperationLogEntry> findRecent(int limit) {
        return jpa.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, Math.max(1, limit)));
    }
}
