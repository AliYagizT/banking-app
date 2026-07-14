package com.bank.domain.model;

import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A customer's credit application and its decision. The application is created in
 * {@link CreditApplicationStatus#SUBMITTED} and routed to the customer's assigned
 * banker, who moves it to a terminal {@code APPROVED} (triggering disbursement) or
 * {@code REJECTED}. The interest rate and computed monthly installment are snapshotted
 * at submission time so the offer the customer saw does not drift with product changes.
 */
@Entity
@Table(name = "credit_application")
public class CreditApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** The banker who owns/evaluates this application (the customer's assigned banker). */
    @Column(name = "banker_id")
    private Long bankerId;

    @Column(name = "product_code", nullable = false, length = 30)
    private String productCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "annual_interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualInterestRate;

    @Column(name = "monthly_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "profession", nullable = false, length = 120)
    private String profession;

    @Column(name = "employment_months", nullable = false)
    private int employmentMonths;

    @Column(name = "disbursement_account_id")
    private Long disbursementAccountId;

    @Column(name = "monthly_installment", precision = 19, scale = 2)
    private BigDecimal monthlyInstallment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditApplicationStatus status = CreditApplicationStatus.SUBMITTED;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CreditApplication() {
        // Required by JPA.
    }

    public CreditApplication(Long customerId, Long bankerId, String productCode,
                             BigDecimal amount, int termMonths, BigDecimal annualInterestRate,
                             BigDecimal monthlyIncome, String profession, int employmentMonths,
                             Long disbursementAccountId, BigDecimal monthlyInstallment) {
        this.customerId = customerId;
        this.bankerId = bankerId;
        this.productCode = productCode;
        this.amount = amount;
        this.termMonths = termMonths;
        this.annualInterestRate = annualInterestRate;
        this.monthlyIncome = monthlyIncome;
        this.profession = profession;
        this.employmentMonths = employmentMonths;
        this.disbursementAccountId = disbursementAccountId;
        this.monthlyInstallment = monthlyInstallment;
        this.status = CreditApplicationStatus.SUBMITTED;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = CreditApplicationStatus.SUBMITTED;
        }
    }

    /** Banker approves the application. Only a SUBMITTED application can be decided. */
    public void approve(Long decidingBankerId, String reason) {
        requireSubmitted();
        this.bankerId = decidingBankerId;
        this.status = CreditApplicationStatus.APPROVED;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    /** Banker rejects the application. Only a SUBMITTED application can be decided. */
    public void reject(Long decidingBankerId, String reason) {
        requireSubmitted();
        this.bankerId = decidingBankerId;
        this.status = CreditApplicationStatus.REJECTED;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    /** Record that the approved funds have been credited to the customer's account. */
    public void markDisbursed(Instant when) {
        if (status != CreditApplicationStatus.APPROVED) {
            throw new ValidationException("Only an approved application can be disbursed");
        }
        this.disbursedAt = when;
    }

    private void requireSubmitted() {
        if (status != CreditApplicationStatus.SUBMITTED) {
            throw new ValidationException(
                    "Application " + id + " is already " + status + " and cannot be decided again");
        }
    }

    public boolean isSubmitted() {
        return status == CreditApplicationStatus.SUBMITTED;
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

    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public String getProfession() {
        return profession;
    }

    public int getEmploymentMonths() {
        return employmentMonths;
    }

    public Long getDisbursementAccountId() {
        return disbursementAccountId;
    }

    public BigDecimal getMonthlyInstallment() {
        return monthlyInstallment;
    }

    public CreditApplicationStatus getStatus() {
        return status;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CreditApplication that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
