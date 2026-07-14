package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.AccountListResponse;
import com.bank.adapter.in.web.dto.AccountResponse;
import com.bank.adapter.in.web.dto.AmountRequest;
import com.bank.adapter.in.web.dto.BalanceResponse;
import com.bank.adapter.in.web.dto.MoneyMovementResponse;
import com.bank.adapter.in.web.dto.OpenAccountRequest;
import com.bank.adapter.in.web.dto.PagedResponse;
import com.bank.adapter.in.web.dto.TransactionHistoryItem;
import com.bank.application.model.PageQuery;
import com.bank.application.port.in.DepositUseCase;
import com.bank.application.port.in.GetAccountStatementUseCase;
import com.bank.application.port.in.GetAccountUseCase;
import com.bank.application.port.in.GetBalanceUseCase;
import com.bank.application.port.in.ListAccountsUseCase;
import com.bank.application.port.in.OpenAccountUseCase;
import com.bank.application.port.in.WithdrawUseCase;
import com.bank.application.service.AccountAccessGuard;
import com.bank.domain.model.Account;
import com.bank.infrastructure.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    /** Standard header carrying the client-supplied idempotency key for money movements. */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final OpenAccountUseCase openAccountUseCase;
    private final GetAccountUseCase getAccountUseCase;
    private final ListAccountsUseCase listAccountsUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final GetAccountStatementUseCase getAccountStatementUseCase;
    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final AccountAccessGuard accountAccessGuard;

    public AccountController(OpenAccountUseCase openAccountUseCase,
                            GetAccountUseCase getAccountUseCase,
                            ListAccountsUseCase listAccountsUseCase,
                            GetBalanceUseCase getBalanceUseCase,
                            GetAccountStatementUseCase getAccountStatementUseCase,
                            DepositUseCase depositUseCase,
                            WithdrawUseCase withdrawUseCase,
                            AccountAccessGuard accountAccessGuard) {
        this.openAccountUseCase = openAccountUseCase;
        this.getAccountUseCase = getAccountUseCase;
        this.listAccountsUseCase = listAccountsUseCase;
        this.getBalanceUseCase = getBalanceUseCase;
        this.getAccountStatementUseCase = getAccountStatementUseCase;
        this.depositUseCase = depositUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.accountAccessGuard = accountAccessGuard;
    }

    @PostMapping
    @Operation(summary = "Open an account for the authenticated customer")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request,
                                                @AuthenticationPrincipal CustomerPrincipal principal,
                                                UriComponentsBuilder uriBuilder) {
        Account account = openAccountUseCase.openAccount(principal.getCustomerId(), request.currency());
        URI location = uriBuilder.path("/api/accounts/{id}").buildAndExpand(account.getId()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping
    @Operation(summary = "List the authenticated customer's own accounts")
    public AccountListResponse list(@AuthenticationPrincipal CustomerPrincipal principal) {
        return AccountListResponse.from(listAccountsUseCase.listAccounts(principal.getCustomerId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of your accounts by id")
    public AccountResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomerPrincipal principal) {
        accountAccessGuard.requireOwnership(id, principal.getCustomerId());
        return AccountResponse.from(getAccountUseCase.getAccount(id));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get one of your account's current balance")
    public BalanceResponse balance(@PathVariable Long id, @AuthenticationPrincipal CustomerPrincipal principal) {
        accountAccessGuard.requireOwnership(id, principal.getCustomerId());
        return BalanceResponse.from(getBalanceUseCase.getBalance(id));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "List one of your account's transaction history (newest first, paginated)")
    public PagedResponse<TransactionHistoryItem> transactions(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-" + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        accountAccessGuard.requireOwnership(id, principal.getCustomerId());
        PageQuery query = new PageQuery(Math.max(page, 0), clampSize(size));
        return PagedResponse.from(
                getAccountStatementUseCase.getStatement(id, query).map(TransactionHistoryItem::from));
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Deposit money into one of your accounts")
    public MoneyMovementResponse deposit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Parameter(description = "Idempotency key; replaying it returns the original result")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        accountAccessGuard.requireOwnership(id, principal.getCustomerId());
        return MoneyMovementResponse.from(
                depositUseCase.deposit(id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/withdrawals")
    @Operation(summary = "Withdraw money from one of your accounts")
    public MoneyMovementResponse withdraw(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Parameter(description = "Idempotency key; replaying it returns the original result")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        accountAccessGuard.requireOwnership(id, principal.getCustomerId());
        return MoneyMovementResponse.from(
                withdrawUseCase.withdraw(id, request.amount(), idempotencyKey));
    }

    private static int clampSize(int requested) {
        if (requested < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
