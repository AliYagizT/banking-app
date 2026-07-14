package com.bank.adapter.out.persistence;

import com.bank.domain.model.OperationLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository for the audit/operation log (an outbound-adapter detail). */
public interface OperationLogJpaRepository extends JpaRepository<OperationLogEntry, Long> {

    Optional<OperationLogEntry> findByOperationId(UUID operationId);

    long countByPrimaryAccountId(Long primaryAccountId);
}
