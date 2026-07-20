package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.BankerApplicationDetailResponse;
import com.bank.adapter.in.web.dto.CreditApplicationResponse;
import com.bank.adapter.in.web.dto.CreditDecisionRequest;
import com.bank.application.port.in.EvaluateCreditApplicationUseCase;
import com.bank.infrastructure.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Banker-facing credit endpoints. A banker sees only the applications of the customers
 * assigned to them: their pending queue, an application's underwriting metrics, and the
 * approve/reject decision. Access is restricted to the BANKER role (see SecurityConfig)
 * and further narrowed to assigned customers in the service layer.
 */
@RestController
@RequestMapping("/api/banker/credit-applications")
@Tag(name = "Credit (Banker)")
public class BankerCreditController {

    private final EvaluateCreditApplicationUseCase evaluateUseCase;

    public BankerCreditController(EvaluateCreditApplicationUseCase evaluateUseCase) {
        this.evaluateUseCase = evaluateUseCase;
    }

    @GetMapping
    @Operation(summary = "Your pending queue: submitted applications of your assigned customers")
    public List<CreditApplicationResponse> queue(@AuthenticationPrincipal CustomerPrincipal principal) {
        return evaluateUseCase.queueForBanker(principal.getCustomerId()).stream()
                .map(CreditApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail with underwriting criteria")
    public BankerApplicationDetailResponse detail(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomerPrincipal principal) {
        Long bankerId = principal.getCustomerId();
        return BankerApplicationDetailResponse.of(
                evaluateUseCase.getForBanker(id, bankerId),
                evaluateUseCase.assess(id, bankerId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve the application and disburse funds to the customer's account")
    public CreditApplicationResponse approve(@PathVariable Long id,
                                             @AuthenticationPrincipal CustomerPrincipal principal,
                                             @Valid @RequestBody(required = false) CreditDecisionRequest request) {
        String reason = request == null ? null : request.reason();
        return CreditApplicationResponse.from(
                evaluateUseCase.approve(id, principal.getCustomerId(), reason));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject the application (no money moves)")
    public CreditApplicationResponse reject(@PathVariable Long id,
                                            @AuthenticationPrincipal CustomerPrincipal principal,
                                            @Valid @RequestBody(required = false) CreditDecisionRequest request) {
        String reason = request == null ? null : request.reason();
        return CreditApplicationResponse.from(
                evaluateUseCase.reject(id, principal.getCustomerId(), reason));
    }
}
