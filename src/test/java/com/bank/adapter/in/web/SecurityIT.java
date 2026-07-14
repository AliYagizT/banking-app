package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.in.web.dto.AmountRequest;
import com.bank.adapter.in.web.dto.TransferRequest;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.application.service.MoneyMovementService;
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
 * Phase 4 (Security): authentication is required, and a customer may only access and
 * operate on their own accounts. Cross-customer access is hidden as 404 to avoid
 * leaking whether another customer's account id exists.
 */
@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTest {

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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestCustomer(Customer customer, String email, Account account) {
        Long accountId() {
            return account.getId();
        }
    }

    private TestCustomer newCustomerWithFundedAccount(String name, String amount) {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        Customer customer = customerService.register(name, email, PASSWORD);
        Account account = accountService.openAccount(customer.getId(), "USD");
        moneyMovementService.deposit(account.getId(), new BigDecimal(amount), UUID.randomUUID().toString());
        return new TestCustomer(customer, email, account);
    }

    // ---- authentication ------------------------------------------------

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("alice", "50.00");

        mockMvc.perform(get("/api/accounts/{id}/balance", owner.accountId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void wrongPasswordIsRejectedWith401() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("bob", "50.00");

        mockMvc.perform(get("/api/accounts/{id}/balance", owner.accountId())
                        .with(httpBasic(owner.email(), "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedOwnerCanReadOwnBalance() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("carol", "50.00");

        mockMvc.perform(get("/api/accounts/{id}/balance", owner.accountId())
                        .with(httpBasic(owner.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    // ---- authorization (own accounts only) -----------------------------

    @Test
    void customerCannotReadAnotherCustomersBalance() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("dave", "50.00");
        TestCustomer intruder = newCustomerWithFundedAccount("eve", "0.01");

        mockMvc.perform(get("/api/accounts/{id}/balance", owner.accountId())
                        .with(httpBasic(intruder.email(), PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void customerCannotDepositIntoAnotherCustomersAccount() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("frank", "50.00");
        TestCustomer intruder = newCustomerWithFundedAccount("grace", "0.01");

        mockMvc.perform(post("/api/accounts/{id}/deposits", owner.accountId())
                        .with(httpBasic(intruder.email(), PASSWORD))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCannotTransferFromAccountTheyDoNotOwn() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("heidi", "100.00");
        TestCustomer intruder = newCustomerWithFundedAccount("ivan", "0.01");

        // Intruder tries to pull money out of the owner's account into their own.
        mockMvc.perform(post("/api/transfers")
                        .with(httpBasic(intruder.email(), PASSWORD))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(
                                owner.accountId(), intruder.accountId(), new BigDecimal("40.00")))))
                .andExpect(status().isNotFound());

        // The owner's balance is untouched.
        mockMvc.perform(get("/api/accounts/{id}/balance", owner.accountId())
                        .with(httpBasic(owner.email(), PASSWORD)))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void customerCannotReadAnotherCustomersRecord() throws Exception {
        TestCustomer owner = newCustomerWithFundedAccount("judy", "1.00");
        TestCustomer intruder = newCustomerWithFundedAccount("mallory", "1.00");

        mockMvc.perform(get("/api/customers/{id}", owner.customer().getId())
                        .with(httpBasic(intruder.email(), PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
