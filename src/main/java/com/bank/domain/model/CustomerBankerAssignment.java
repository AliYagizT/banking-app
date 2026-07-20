package com.bank.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Links a customer to the banker responsible for their credit applications. Assigned
 * (randomly) when the customer registers; one active assignment per customer.
 */
@Entity
@Table(name = "customer_banker_assignment")
public class CustomerBankerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private Long customerId;

    @Column(name = "banker_id", nullable = false)
    private Long bankerId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected CustomerBankerAssignment() {
        // Required by JPA.
    }

    public CustomerBankerAssignment(Long customerId, Long bankerId) {
        this.customerId = customerId;
        this.bankerId = bankerId;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getBankerId() {
        return bankerId;
    }

    public void setBankerId(Long bankerId) {
        this.bankerId = bankerId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerBankerAssignment that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
