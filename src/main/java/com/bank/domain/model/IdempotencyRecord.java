package com.bank.domain.model;

import com.bank.domain.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted idempotency key for a money-moving operation. The unique key column
 * turns a replayed request into a constraint collision; the stored {@code responsePayload}
 * is then returned so the operation is never applied twice.
 *
 * <p>{@code requestHash} is a digest of the request parameters, used to detect the
 * same key being reused with different parameters (a client error, not a retry).
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 20)
    private OperationType operationType;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // Required by JPA.
    }

    public IdempotencyRecord(String idempotencyKey,
                             OperationType operationType,
                             String requestHash,
                             UUID operationId,
                             String responsePayload) {
        this.idempotencyKey = idempotencyKey;
        this.operationType = operationType;
        this.requestHash = requestHash;
        this.operationId = operationId;
        this.responsePayload = responsePayload;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
