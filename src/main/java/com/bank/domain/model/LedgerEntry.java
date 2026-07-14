package com.bank.domain.model;

import com.bank.domain.LedgerDirection;
import com.bank.domain.Money;
import com.bank.domain.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single, immutable leg of the double-entry ledger. Once created, a ledger entry
 * is never updated or deleted — corrections are made by appending new compensating
 * entries. Accordingly this entity exposes no setters and is not versioned.
 *
 * <p>Each entry carries the {@code operationId} of the money movement that produced
 * it (linking the two legs of a transfer, or the customer/external legs of a
 * deposit/withdrawal) and a {@code createdAt} timestamp, satisfying the audit
 * requirement.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 20)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 6)
    private LedgerDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // Required by JPA.
    }

    public LedgerEntry(Account account,
                       UUID operationId,
                       OperationType operationType,
                       LedgerDirection direction,
                       BigDecimal amount,
                       BigDecimal balanceAfter) {
        this.account = account;
        this.operationId = operationId;
        this.operationType = operationType;
        this.direction = direction;
        this.amount = Money.normalize(amount);
        this.balanceAfter = Money.normalize(balanceAfter);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Signed effect of this entry on its account's balance (+amount for credit, −amount for debit). */
    public BigDecimal signedAmount() {
        return direction == LedgerDirection.CREDIT ? amount : amount.negate();
    }
}
