package com.bank.domain.model;

import com.bank.domain.AccountStatus;
import com.bank.domain.AccountType;
import com.bank.domain.Money;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An account whose authoritative balance is the sum of its ledger entries. The
 * {@code balance} column here is a cache for performance; it is only ever mutated
 * inside the same transaction that appends the corresponding ledger entries.
 *
 * <p>The {@link Version} field enables optimistic locking: concurrent transactions
 * that both read this row and try to write it will collide, and all but one will
 * fail with an optimistic-lock exception (which the service retries). This is
 * preferred over pessimistic row locks here because contention on a single account
 * is normally low, and optimistic locking avoids holding database locks across the
 * transaction while still guaranteeing no lost updates.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType = AccountType.CUSTOMER;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = Money.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Account() {
        // Required by JPA.
    }

    public Account(String accountNumber, Customer customer, String currency) {
        this.accountNumber = accountNumber;
        this.customer = customer;
        this.currency = currency;
        this.accountType = AccountType.CUSTOMER;
        this.status = AccountStatus.ACTIVE;
        this.balance = Money.ZERO;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (balance == null) {
            balance = Money.ZERO;
        }
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isSystemAccount() {
        return accountType == AccountType.SYSTEM;
    }

    /**
     * Apply a money movement to the cached balance. The caller is responsible for
     * doing this inside the same transaction that writes the ledger entry, and for
     * enforcing business rules (e.g. sufficient funds) beforehand.
     */
    public void applyCredit(BigDecimal amount) {
        this.balance = Money.normalize(this.balance.add(amount));
    }

    public void applyDebit(BigDecimal amount) {
        this.balance = Money.normalize(this.balance.subtract(amount));
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
