package com.bank.application.service;

import com.bank.application.model.UnderwritingAssessment;
import com.bank.application.port.in.DisburseCreditUseCase;
import com.bank.application.port.in.EvaluateCreditApplicationUseCase;
import com.bank.application.port.out.CreditApplicationRepository;
import com.bank.application.port.out.CreditProductRepository;
import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.Money;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.model.CreditApplication;
import com.bank.domain.model.CreditProduct;
import com.bank.domain.model.RepaymentPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * The banker side of credit analysis. Bankers review the SUBMITTED applications of the
 * customers assigned to them, see decision-support metrics (installment/income ratio,
 * employment tenure, product limits), and decide. Approving triggers disbursement of the
 * funds into the customer's account via {@link DisburseCreditUseCase}; rejecting moves no
 * money. Every entry point authorizes the banker against the application's customer
 * through {@link BankerAccessGuard}.
 */
@Service
public class CreditEvaluationService implements EvaluateCreditApplicationUseCase {

    private static final MathContext RATIO_MC = new MathContext(10, RoundingMode.HALF_EVEN);

    private final CreditApplicationRepository applicationRepository;
    private final CreditProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final BankerAccessGuard bankerAccessGuard;
    private final DisburseCreditUseCase disburseCreditUseCase;
    private final TransactionRunner transactionRunner;

    private final BigDecimal maxInstallmentToIncome;
    private final int minEmploymentMonths;

    public CreditEvaluationService(CreditApplicationRepository applicationRepository,
                                   CreditProductRepository productRepository,
                                   CustomerRepository customerRepository,
                                   BankerAccessGuard bankerAccessGuard,
                                   DisburseCreditUseCase disburseCreditUseCase,
                                   TransactionRunner transactionRunner,
                                   @Value("${banking.credit.max-installment-to-income:0.50}") BigDecimal maxInstallmentToIncome,
                                   @Value("${banking.credit.min-employment-months:3}") int minEmploymentMonths) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.bankerAccessGuard = bankerAccessGuard;
        this.disburseCreditUseCase = disburseCreditUseCase;
        this.transactionRunner = transactionRunner;
        this.maxInstallmentToIncome = maxInstallmentToIncome;
        this.minEmploymentMonths = minEmploymentMonths;
    }

    @Override
    public List<CreditApplication> queueForBanker(Long bankerId) {
        return transactionRunner.inReadOnlyTransaction(() ->
                applicationRepository.findByBankerIdAndStatus(bankerId, CreditApplicationStatus.SUBMITTED));
    }

    @Override
    public CreditApplication getForBanker(Long applicationId, Long bankerId) {
        return transactionRunner.inReadOnlyTransaction(() -> loadManaged(applicationId, bankerId));
    }

    @Override
    public UnderwritingAssessment assess(Long applicationId, Long bankerId) {
        return transactionRunner.inReadOnlyTransaction(() -> {
            CreditApplication application = loadManaged(applicationId, bankerId);
            return assessInternal(application);
        });
    }

    @Override
    public CreditApplication approve(Long applicationId, Long bankerId, String reason) {
        // 1) Authorize + flip to APPROVED in its own transaction (optimistic lock guards
        //    against two bankers deciding the same application concurrently).
        CreditApplication approved = transactionRunner.inNewTransaction(() -> {
            CreditApplication application = loadManaged(applicationId, bankerId);
            application.approve(bankerId, reason);
            return applicationRepository.save(application);
        });

        // 2) Disburse the funds. Idempotent on the application id, so a retried approve
        //    (or a crash between steps) can never double-credit the customer.
        disburseCreditUseCase.disburse(
                approved.getDisbursementAccountId(),
                approved.getAmount(),
                "credit-disbursement-" + approved.getId());

        // 3) Stamp the disbursement time.
        return transactionRunner.inNewTransaction(() -> {
            CreditApplication application = applicationRepository.findById(applicationId)
                    .orElseThrow(() -> NotFoundException.creditApplication(applicationId));
            application.markDisbursed(Instant.now());
            return applicationRepository.save(application);
        });
    }

    @Override
    public CreditApplication reject(Long applicationId, Long bankerId, String reason) {
        return transactionRunner.inNewTransaction(() -> {
            CreditApplication application = loadManaged(applicationId, bankerId);
            application.reject(bankerId, reason);
            return applicationRepository.save(application);
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private CreditApplication loadManaged(Long applicationId, Long bankerId) {
        CreditApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> NotFoundException.creditApplication(applicationId));
        bankerAccessGuard.requireManages(application.getCustomerId(), bankerId, applicationId);
        return application;
    }

    private UnderwritingAssessment assessInternal(CreditApplication application) {
        BigDecimal installment = application.getMonthlyInstallment();
        BigDecimal income = application.getMonthlyIncome();
        BigDecimal ratio = income.signum() > 0
                ? installment.divide(income, RATIO_MC)
                : BigDecimal.ONE; // no income -> treat as fully over the limit
        boolean withinIncomeLimit = ratio.compareTo(maxInstallmentToIncome) <= 0;

        boolean meetsEmployment = application.getEmploymentMonths() >= minEmploymentMonths;

        boolean withinProductLimits = productRepository.findByCode(application.getProductCode())
                .map(product -> withinLimits(product, application))
                .orElse(false);

        boolean customerActive = customerRepository.findById(application.getCustomerId())
                .map(com.bank.domain.model.Customer::isActive)
                .orElse(false);

        RepaymentPlan plan = CreditApplicationService.repaymentPlanFor(application);

        boolean meetsAll = withinIncomeLimit && meetsEmployment && withinProductLimits && customerActive;

        return new UnderwritingAssessment(
                Money.normalize(installment),
                ratio,
                maxInstallmentToIncome,
                withinIncomeLimit,
                application.getEmploymentMonths(),
                minEmploymentMonths,
                meetsEmployment,
                withinProductLimits,
                customerActive,
                plan.totalPayment(),
                plan.totalInterest(),
                meetsAll);
    }

    private static boolean withinLimits(CreditProduct product, CreditApplication application) {
        BigDecimal amount = application.getAmount();
        return amount.compareTo(product.getMinAmount()) >= 0
                && amount.compareTo(product.getMaxAmount()) <= 0
                && application.getTermMonths() <= product.getMaxTermMonths();
    }
}
