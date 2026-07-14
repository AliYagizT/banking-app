package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import com.bank.infrastructure.security.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force guard: repeated failed logins from one IP get blocked (HTTP 429), even
 * for a subsequently-correct password, until the cool-off elapses. Test threshold is 3
 * (see {@code src/test/resources/application.yml}).
 */
@AutoConfigureMockMvc
class BruteForceIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LoginAttemptService loginAttemptService;

    // Isolate this class: the per-IP counter is a shared singleton and every MockMvc
    // request originates from 127.0.0.1.
    @BeforeEach
    @AfterEach
    void resetCounters() {
        loginAttemptService.clear();
    }

    @Test
    void repeatedFailedLoginsBlockTheIpWith429() throws Exception {
        String email = "brute-" + UUID.randomUUID() + "@example.com";
        Customer customer = customerService.register("Brute Target", email, PASSWORD);
        Account account = accountService.openAccount(customer.getId(), "USD");
        String path = "/api/accounts/" + account.getId() + "/balance";

        // Three wrong-password attempts are rejected as unauthorized (threshold = 3).
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(get(path).with(httpBasic(email, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        // Now the IP is blocked: even the CORRECT password is refused with 429.
        mockMvc.perform(get(path).with(httpBasic(email, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }
}
