package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.AccountResponse;
import com.bank.adapter.in.web.dto.CreateBankerRequest;
import com.bank.adapter.in.web.dto.CustomerResponse;
import com.bank.adapter.in.web.dto.OperationLogResponse;
import com.bank.application.port.in.AdminAccountUseCase;
import com.bank.application.port.in.AdminUserUseCase;
import com.bank.application.port.in.CreateBankerCommand;
import com.bank.domain.model.Customer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Administrative endpoints. The whole {@code /api/admin/**} tree is restricted to the
 * ADMIN role in {@code SecurityConfig}; a non-admin caller receives 403.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminAccountUseCase adminAccountUseCase;
    private final AdminUserUseCase adminUserUseCase;

    public AdminController(AdminAccountUseCase adminAccountUseCase, AdminUserUseCase adminUserUseCase) {
        this.adminAccountUseCase = adminAccountUseCase;
        this.adminUserUseCase = adminUserUseCase;
    }

    // ---- accounts ----

    @GetMapping("/accounts/{id}")
    @Operation(summary = "View any account (ADMIN)")
    public AccountResponse view(@PathVariable Long id) {
        return AccountResponse.from(adminAccountUseCase.view(id));
    }

    @PostMapping("/accounts/{id}/freeze")
    @Operation(summary = "Freeze an account (ADMIN)")
    public AccountResponse freeze(@PathVariable Long id) {
        return AccountResponse.from(adminAccountUseCase.freeze(id));
    }

    @PostMapping("/accounts/{id}/close")
    @Operation(summary = "Close an account (ADMIN)")
    public AccountResponse close(@PathVariable Long id) {
        return AccountResponse.from(adminAccountUseCase.close(id));
    }

    // ---- users & staff ----

    @GetMapping("/users")
    @Operation(summary = "List all users with their roles (ADMIN)")
    public List<CustomerResponse> users() {
        return adminUserUseCase.listUsers().stream().map(CustomerResponse::from).toList();
    }

    @PostMapping("/bankers")
    @Operation(summary = "Add a banker: creates a Firebase login and a BANKER record (ADMIN)")
    public ResponseEntity<CustomerResponse> addBanker(@Valid @RequestBody CreateBankerRequest request,
                                                      UriComponentsBuilder uriBuilder) {
        Customer banker = adminUserUseCase.createBanker(
                new CreateBankerCommand(request.fullName(), request.email(), request.password()));
        URI location = uriBuilder.path("/api/customers/{id}").buildAndExpand(banker.getId()).toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(banker));
    }

    // ---- audit log ----

    @GetMapping("/operations")
    @Operation(summary = "Recent operation/audit log, newest first (ADMIN)")
    public List<OperationLogResponse> operations(@RequestParam(defaultValue = "100") int limit) {
        return adminUserUseCase.recentOperations(limit).stream().map(OperationLogResponse::from).toList();
    }
}
