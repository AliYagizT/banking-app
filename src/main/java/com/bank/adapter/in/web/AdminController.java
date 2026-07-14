package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.AccountResponse;
import com.bank.application.port.in.AdminAccountUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints. The whole {@code /api/admin/**} tree is restricted to the
 * ADMIN role in {@code SecurityConfig}; a non-admin caller receives 403.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminAccountUseCase adminAccountUseCase;

    public AdminController(AdminAccountUseCase adminAccountUseCase) {
        this.adminAccountUseCase = adminAccountUseCase;
    }

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
}
