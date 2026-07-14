package com.bank.domain.model;

import com.bank.domain.Money;
import com.bank.domain.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable audit record per executed money movement (the operation-centric
 * complement to the double-entry {@link LedgerEntry}). Exposes no setters; rows are
 * appended within the same transaction that writes the ledger and are never modified.
 */
@Entity
@Table(name = "operation_log")
public class OperationLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 20)
    private OperationType operationType;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "primary_account_id", nullable = false, updatable = false)
    private Long primaryAccountId;

    /** The other account involved: external cash for deposit/withdraw, destination for transfer. */
    @Column(name = "counter_account_id", updatable = false)
    private Long counterAccountId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OperationLogEntry() {
        // Required by JPA.
    }

    public OperationLogEntry(UUID operationId,
                            OperationType operationType,
                            String idempotencyKey,
                            Long primaryAccountId,
                            Long counterAccountId,
                            BigDecimal amount) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.idempotencyKey = idempotencyKey;
        this.primaryAccountId = primaryAccountId;
        this.counterAccountId = counterAccountId;
        this.amount = Money.normalize(amount);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getPrimaryAccountId() {
        return primaryAccountId;
    }

    public Long getCounterAccountId() {
        return counterAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
