package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.CustomerJpaRepository;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.application.service.MoneyMovementService;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.bank.support.TestAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin console: an ADMIN can list users, add a banker (identity account provisioned via
 * the fake provisioner), and read the operation/audit log. A non-admin is forbidden.
 */
@AutoConfigureMockMvc
class AdminUserIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerService customerService;
    @Autowired private AccountService accountService;
    @Autowired private MoneyMovementService moneyMovementService;
    @Autowired private CustomerJpaRepository customerJpaRepository;

    private String newCustomer() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
        customerService.register("Cust", email);
        return email;
    }

    private String newAdmin() {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        Customer admin = customerService.register("Admin", email);
        Customer managed = customerJpaRepository.findById(admin.getId()).orElseThrow();
        managed.setRole(CustomerRole.ADMIN);
        customerJpaRepository.save(managed);
        return email;
    }

    @Test
    void adminCanListUsersAndAddBanker() throws Exception {
        String admin = newAdmin();
        String bankerEmail = "new-banker-" + UUID.randomUUID() + "@bank.local";

        // Add a banker.
        String body = "{\"fullName\":\"Yeni Bankaci\",\"email\":\"" + bankerEmail + "\",\"password\":\"banker123\"}";
        mockMvc.perform(post("/api/admin/bankers")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("BANKER"))
                .andExpect(jsonPath("$.email").value(bankerEmail));

        // It now shows up in the user list.
        mockMvc.perform(get("/api/admin/users").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + bankerEmail + "' && @.role == 'BANKER')]").exists());
    }

    @Test
    void adminSeesTheOperationLog() throws Exception {
        String admin = newAdmin();
        String customer = newCustomer();
        Account account = accountService.openAccount(
                customerJpaRepository.findByEmail(customer).orElseThrow().getId(), "USD");
        moneyMovementService.deposit(account.getId(), new BigDecimal("25.00"), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/admin/operations?limit=50").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[?(@.primaryAccountId == " + account.getId() + ")]").exists());
    }

    @Test
    void nonAdminIsForbiddenFromAdminUserEndpoints() throws Exception {
        String customer = newCustomer();
        mockMvc.perform(get("/api/admin/users").with(bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
