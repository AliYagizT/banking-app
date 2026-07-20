package com.bank.application.service;

import com.bank.application.port.in.GetCreditApplicationUseCase;
import com.bank.application.port.in.GetRepaymentPlanUseCase;
import com.bank.application.port.in.ListCreditApplicationsUseCase;
import com.bank.application.port.in.ListCreditProductsUseCase;
import com.bank.application.port.in.SubmitCreditApplicationCommand;
import com.bank.application.port.in.SubmitCreditApplicationUseCase;
import com.bank.application.port.out.AccountRepository;
import com.bank.application.port.out.CreditApplicationRepository;
import com.bank.application.port.out.CreditProductRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.Money;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.CreditApplication;
import com.bank.domain.model.CreditProduct;
import com.bank.domain.model.RepaymentPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Customer-facing credit operations: browse products, submit an application (routed to
 * the customer's assigned banker), list one's own applications, and view the repayment
 * plan. All decisions are made by a banker (see {@link CreditEvaluationService}); this
 * service never approves or moves money.
 */
@Service
public class CreditApplicationService implements
        SubmitCreditApplicationUseCase, ListCreditApplicationsUseCase,
        GetCreditApplicationUseCase, GetRepaymentPlanUseCase, ListCreditProductsUseCase {

    private final CreditApplicationRepository applicationRepository;
    private final CreditProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final BankerAssignmentService bankerAssignmentService;
    private final TransactionRunner transactionRunner;

    public CreditApplicationService(CreditApplicationRepository applicationRepository,
                                    CreditProductRepository productRepository,
                                    AccountRepository accountRepository,
                                    BankerAssignmentService bankerAssignmentService,
                                    TransactionRunner transactionRunner) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
        this.bankerAssignmentService = bankerAssignmentService;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public List<CreditProduct> list() {
        return transactionRunner.inReadOnlyTransaction(productRepository::findAll);
    }

    @Override
    public CreditApplication submit(SubmitCreditApplicationCommand command) {
        Long customerId = requireId(command.customerId(), "customerId");
        Long accountId = requireId(command.disbursementAccountId(), "disbursementAccountId");
        String productCode = requireText(command.productCode(), "productCode");
        String profession = requireText(command.profession(), "profession");
        BigDecimal amount = requirePositiveMoney(command.amount(), "amount");
        BigDecimal monthlyIncome = requirePositiveMoney(command.monthlyIncome(), "monthlyIncome");
        int termMonths = command.termMonths();
        int employmentMonths = command.employmentMonths();
        if (termMonths < 1) {
            throw new ValidationException("termMonths must be at least 1");
        }
        if (employmentMonths < 0) {
            throw new ValidationException("employmentMonths must not be negative");
        }

        return transactionRunner.inNewTransaction(() -> {
            CreditProduct product = productRepository.findByCode(productCode)
                    .orElseThrow(() -> NotFoundException.creditProduct(productCode));
            requireWithinProductLimits(product, amount, termMonths);

            // The disbursement account must belong to the applying customer.
            if (!accountRepository.existsByIdAndCustomerId(accountId, customerId)) {
                throw NotFoundException.account(accountId);
            }

            Long bankerId = bankerAssignmentService.getOrAssignBanker(customerId).orElse(null);
            BigDecimal annualRate = product.getAnnualInterestRate();
            BigDecimal monthlyInstallment =
                    RepaymentPlan.monthlyPaymentFromAnnual(amount, annualRate, termMonths);

            CreditApplication application = new CreditApplication(
                    customerId, bankerId, product.getCode(), amount, termMonths, annualRate,
                    monthlyIncome, profession, employmentMonths, accountId, monthlyInstallment);
            return applicationRepository.save(application);
        });
    }

    @Override
    public List<CreditApplication> listForCustomer(Long customerId) {
        return transactionRunner.inReadOnlyTransaction(
                () -> applicationRepository.findByCustomerId(customerId));
    }

    @Override
    public CreditApplication getForCustomer(Long applicationId, Long customerId) {
        return transactionRunner.inReadOnlyTransaction(
                () -> loadOwned(applicationId, customerId));
    }

    @Override
    public RepaymentPlan repaymentPlanForCustomer(Long applicationId, Long customerId) {
        return transactionRunner.inReadOnlyTransaction(() -> {
            CreditApplication application = loadOwned(applicationId, customerId);
            return repaymentPlanFor(application);
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds the amortization schedule from the application's snapshotted terms. */
    static RepaymentPlan repaymentPlanFor(CreditApplication application) {
        // First installment falls one month after disbursement (or, for a not-yet-approved
        // application, one month from today as an indicative offer).
        Instant base = application.getDisbursedAt() != null
                ? application.getDisbursedAt()
                : Instant.now();
        LocalDate firstDueDate = LocalDate.ofInstant(base, ZoneOffset.UTC).plusMonths(1);
        return RepaymentPlan.generate(
                application.getAmount(),
                application.getAnnualInterestRate(),
                application.getTermMonths(),
                firstDueDate);
    }

    private CreditApplication loadOwned(Long applicationId, Long customerId) {
        CreditApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> NotFoundException.creditApplication(applicationId));
        // Hide other customers' applications behind "not found" to prevent id probing.
        if (!application.getCustomerId().equals(customerId)) {
            throw NotFoundException.creditApplication(applicationId);
        }
        return application;
    }

    private static void requireWithinProductLimits(CreditProduct product, BigDecimal amount, int termMonths) {
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw new ValidationException("amount must be between " + product.getMinAmount()
                    + " and " + product.getMaxAmount() + " for product " + product.getCode());
        }
        if (termMonths > product.getMaxTermMonths()) {
            throw new ValidationException("termMonths must not exceed " + product.getMaxTermMonths()
                    + " for product " + product.getCode());
        }
    }

    private static Long requireId(Long value, String field) {
        if (value == null) {
            throw new ValidationException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
        return value.strip();
    }

    private static BigDecimal requirePositiveMoney(BigDecimal value, String field) {
        if (value == null) {
            throw new ValidationException(field + " must not be null");
        }
        if (!Money.isPositive(value)) {
            throw new ValidationException(field + " must be greater than zero");
        }
        if (value.stripTrailingZeros().scale() > Money.SCALE) {
            throw new ValidationException(field + " must not have more than " + Money.SCALE + " decimal places");
        }
        return Money.normalize(value);
    }
}
