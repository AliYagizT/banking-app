package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.in.web.dto.AmountRequest;
import com.bank.adapter.out.persistence.CustomerJpaRepository;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.application.service.MoneyMovementService;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC: the ADMIN role may manage any account via {@code /api/admin/**}; a plain
 * CUSTOMER is forbidden there.
 */
@AutoConfigureMockMvc
class RbacIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private MoneyMovementService moneyMovementService;
    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String newCustomer() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
        customerService.register("Cust", email, PASSWORD);
        return email;
    }

    private String newAdmin() {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        Customer admin = customerService.register("Admin", email, PASSWORD);
        // Elevate to ADMIN (operationally this is done out-of-band, e.g. by the seeder).
        Customer managed = customerJpaRepository.findById(admin.getId()).orElseThrow();
        managed.setRole(CustomerRole.ADMIN);
        customerJpaRepository.save(managed);
        return email;
    }

    private Account accountFor(String email, String fundAmount) {
        Customer customer = customerJpaRepository.findByEmail(email).orElseThrow();
        Account account = accountService.openAccount(customer.getId(), "USD");
        moneyMovementService.deposit(account.getId(), new BigDecimal(fundAmount), UUID.randomUUID().toString());
        return account;
    }

    @Test
    void customerIsForbiddenFromAdminEndpoints() throws Exception {
        String customer = newCustomer();
        Account own = accountFor(customer, "10.00");

        mockMvc.perform(get("/api/admin/accounts/{id}", own.getId())
                        .with(httpBasic(customer, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void adminCanViewAnyAccount() throws Exception {
        String customer = newCustomer();
        Account target = accountFor(customer, "25.00");
        String admin = newAdmin();

        mockMvc.perform(get("/api/admin/accounts/{id}", target.getId())
                        .with(httpBasic(admin, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().intValue()))
                .andExpect(jsonPath("$.balance").value(25.00));
    }

    @Test
    void adminCanFreezeAccountWhichThenBlocksMovement() throws Exception {
        String customer = newCustomer();
        Account target = accountFor(customer, "100.00");
        String admin = newAdmin();

        // Admin freezes the account.
        mockMvc.perform(post("/api/admin/accounts/{id}/freeze", target.getId())
                        .with(httpBasic(admin, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // The owner can no longer move money on a frozen account.
        mockMvc.perform(post("/api/accounts/{id}/deposits", target.getId())
                        .with(httpBasic(customer, PASSWORD))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("5.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }
}
