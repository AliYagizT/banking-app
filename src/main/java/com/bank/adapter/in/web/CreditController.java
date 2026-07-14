package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.CreditApplicationResponse;
import com.bank.adapter.in.web.dto.RepaymentPlanResponse;
import com.bank.adapter.in.web.dto.SubmitCreditApplicationRequest;
import com.bank.application.port.in.GetCreditApplicationUseCase;
import com.bank.application.port.in.GetRepaymentPlanUseCase;
import com.bank.application.port.in.ListCreditApplicationsUseCase;
import com.bank.application.port.in.SubmitCreditApplicationCommand;
import com.bank.application.port.in.SubmitCreditApplicationUseCase;
import com.bank.domain.model.CreditApplication;
import com.bank.infrastructure.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Customer-facing credit endpoints: submit an application, list one's own applications,
 * and view an application's details and repayment plan. Decisions are made by a banker
 * (see {@link BankerCreditController}).
 */
@RestController
@RequestMapping("/api/credit-applications")
@Tag(name = "Credit")
public class CreditController {

    private final SubmitCreditApplicationUseCase submitUseCase;
    private final ListCreditApplicationsUseCase listUseCase;
    private final GetCreditApplicationUseCase getUseCase;
    private final GetRepaymentPlanUseCase repaymentPlanUseCase;

    public CreditController(SubmitCreditApplicationUseCase submitUseCase,
                           ListCreditApplicationsUseCase listUseCase,
                           GetCreditApplicationUseCase getUseCase,
                           GetRepaymentPlanUseCase repaymentPlanUseCase) {
        this.submitUseCase = submitUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.repaymentPlanUseCase = repaymentPlanUseCase;
    }

    @PostMapping
    @Operation(summary = "Submit a credit application (routed to your assigned banker)")
    public ResponseEntity<CreditApplicationResponse> submit(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Valid @RequestBody SubmitCreditApplicationRequest request,
            UriComponentsBuilder uriBuilder) {
        CreditApplication application = submitUseCase.submit(new SubmitCreditApplicationCommand(
                principal.getCustomerId(),
                request.productCode(),
                request.amount(),
                request.termMonths(),
                request.monthlyIncome(),
                request.profession(),
                request.employmentMonths(),
                request.disbursementAccountId()));
        URI location = uriBuilder.path("/api/credit-applications/{id}")
                .buildAndExpand(application.getId()).toUri();
        return ResponseEntity.created(location).body(CreditApplicationResponse.from(application));
    }

    @GetMapping
    @Operation(summary = "List your own credit applications")
    public List<CreditApplicationResponse> list(@AuthenticationPrincipal CustomerPrincipal principal) {
        return listUseCase.listForCustomer(principal.getCustomerId()).stream()
                .map(CreditApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of your own credit applications")
    public CreditApplicationResponse get(@PathVariable Long id,
                                         @AuthenticationPrincipal CustomerPrincipal principal) {
        return CreditApplicationResponse.from(
                getUseCase.getForCustomer(id, principal.getCustomerId()));
    }

    @GetMapping("/{id}/repayment-plan")
    @Operation(summary = "View the repayment schedule (installments) for your credit")
    public RepaymentPlanResponse repaymentPlan(@PathVariable Long id,
                                               @AuthenticationPrincipal CustomerPrincipal principal) {
        return RepaymentPlanResponse.from(
                repaymentPlanUseCase.repaymentPlanForCustomer(id, principal.getCustomerId()));
    }
}
