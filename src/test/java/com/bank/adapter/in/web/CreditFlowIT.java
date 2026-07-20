package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.CustomerJpaRepository;
import com.bank.application.service.AccountService;
import com.bank.application.service.CustomerService;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.bank.support.TestAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end credit analysis flow over the real API + PostgreSQL: a customer applies,
 * their randomly assigned banker reviews the underwriting metrics and approves, the funds
 * are disbursed into the customer's account (visible in the statement), and the customer
 * can see the repayment plan. Also covers the RBAC boundaries around applications.
 */
@AutoConfigureMockMvc
class CreditFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String BANKER_PASSWORD = "banker123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    private String newCustomer() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
        customerService.register("Cust", email);
        return email;
    }

    private Account accountFor(String email) {
        Customer customer = customerJpaRepository.findByEmail(email).orElseThrow();
        return accountService.openAccount(customer.getId(), "USD");
    }

    private String submitBody(Long accountId) {
        return """
                {"productCode":"IHTIYAC","amount":60000.00,"termMonths":12,
                 "monthlyIncome":20000.00,"profession":"Engineer",
                 "employmentMonths":24,"disbursementAccountId":%d}
                """.formatted(accountId);
    }

    private String bankerEmail(long bankerId) {
        return customerJpaRepository.findById(bankerId).orElseThrow().getEmail();
    }

    @Test
    void submit_review_approve_disburses_and_shows_repaymentPlan() throws Exception {
        String customer = newCustomer();
        Account account = accountFor(customer);

        // 1) Customer submits -> SUBMITTED, routed to an assigned banker.
        String submitResponse = mockMvc.perform(post("/api/credit-applications")
                        .with(bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(account.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.monthlyInstallment").isNumber())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(submitResponse);
        long applicationId = created.get("id").asLong();
        long bankerId = created.get("bankerId").asLong();
        String banker = bankerEmail(bankerId);

        // 2) The application is in the assigned banker's queue.
        mockMvc.perform(get("/api/banker/credit-applications")
                        .with(bearer(banker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + applicationId + ")]").exists());

        // 3) Banker sees underwriting metrics; all criteria pass for this applicant.
        mockMvc.perform(get("/api/banker/credit-applications/{id}", applicationId)
                        .with(bearer(banker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.id").value((int) applicationId))
                .andExpect(jsonPath("$.assessment.withinIncomeLimit").value(true))
                .andExpect(jsonPath("$.assessment.meetsAllCriteria").value(true));

        // 4) Banker approves -> disbursement.
        mockMvc.perform(post("/api/banker/credit-applications/{id}/approve", applicationId)
                        .with(bearer(banker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Gelir yeterli\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.disbursedAt").isNotEmpty());

        // 5) Money landed in the customer's account.
        mockMvc.perform(get("/api/accounts/{id}/balance", account.getId())
                        .with(bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60000.00));

        // 6) The disbursement shows up in the transaction history.
        mockMvc.perform(get("/api/accounts/{id}/transactions", account.getId())
                        .with(bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CREDIT_DISBURSEMENT"))
                .andExpect(jsonPath("$.content[0].amount").value(60000.00));

        // 7) The customer can see the repayment schedule (12 installments).
        mockMvc.perform(get("/api/credit-applications/{id}/repayment-plan", applicationId)
                        .with(bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.installments.length()").value(12))
                .andExpect(jsonPath("$.totalPayment").isNumber());
    }

    @Test
    void customerCannotSeeAnotherCustomersApplication() throws Exception {
        String owner = newCustomer();
        Account account = accountFor(owner);
        String submitResponse = mockMvc.perform(post("/api/credit-applications")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(account.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(submitResponse).get("id").asLong();

        String stranger = newCustomer();
        mockMvc.perform(get("/api/credit-applications/{id}", applicationId)
                        .with(bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bankerCannotDecideApplicationOfCustomerNotAssignedToThem() throws Exception {
        String customer = newCustomer();
        Account account = accountFor(customer);
        String submitResponse = mockMvc.perform(post("/api/credit-applications")
                        .with(bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(account.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(submitResponse);
        long applicationId = created.get("id").asLong();
        long assignedBankerId = created.get("bankerId").asLong();

        // The OTHER seeded banker (not the assigned one) must not access this application.
        List<Long> bankerIds = customerJpaRepository.findIdsByRole(CustomerRole.BANKER);
        long otherBankerId = bankerIds.stream().filter(id -> id != assignedBankerId).findFirst().orElseThrow();
        String otherBanker = bankerEmail(otherBankerId);

        mockMvc.perform(get("/api/banker/credit-applications/{id}", applicationId)
                        .with(bearer(otherBanker)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/banker/credit-applications/{id}/approve", applicationId)
                        .with(bearer(otherBanker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
