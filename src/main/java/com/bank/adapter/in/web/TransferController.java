package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.MoneyMovementResponse;
import com.bank.adapter.in.web.dto.TransferRequest;
import com.bank.application.port.in.TransferUseCase;
import com.bank.application.service.AccountAccessGuard;
import com.bank.infrastructure.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferUseCase transferUseCase;
    private final AccountAccessGuard accountAccessGuard;

    public TransferController(TransferUseCase transferUseCase,
                             AccountAccessGuard accountAccessGuard) {
        this.transferUseCase = transferUseCase;
        this.accountAccessGuard = accountAccessGuard;
    }

    @PostMapping
    @Operation(summary = "Transfer money from one of your accounts to another account")
    public MoneyMovementResponse transfer(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Parameter(description = "Idempotency key; replaying it returns the original result")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        // Only the source account must be owned by the caller; the destination may be
        // any account (e.g. paying another customer).
        accountAccessGuard.requireOwnership(request.sourceAccountId(), principal.getCustomerId());
        return MoneyMovementResponse.from(transferUseCase.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                idempotencyKey));
    }
}
