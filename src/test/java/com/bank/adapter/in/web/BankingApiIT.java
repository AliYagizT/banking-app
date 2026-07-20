package com.bank.adapter.in.web;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.in.web.dto.AmountRequest;
import com.bank.adapter.in.web.dto.CreateCustomerRequest;
import com.bank.adapter.in.web.dto.OpenAccountRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static com.bank.support.TestAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BankingApiIT extends AbstractIntegrationTest {

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

    /** An account together with the login email of its owner (password is {@link #PASSWORD}). */
    private record OwnedAccount(Account account, String email) {
        Long id() {
            return account.getId();
        }
    }

    private OwnedAccount newAccount() {
        String email = "api-" + UUID.randomUUID() + "@example.com";
        Customer customer = customerService.register("API User", email);
        Account account = accountService.openAccount(customer.getId(), "USD");
        return new OwnedAccount(account, email);
    }

    private OwnedAccount newFundedAccount(String amount) {
        OwnedAccount owned = newAccount();
        moneyMovementService.deposit(owned.id(), new BigDecimal(amount), UUID.randomUUID().toString());
        return owned;
    }

    // ---- customer & account creation ----------------------------------

    @Test
    void registerCustomerThenFetchItWhenAuthenticated() throws Exception {
        String email = "create-" + UUID.randomUUID() + "@example.com";
        String body = json(new CreateCustomerRequest("Grace Hopper"));

        // Registration requires a verified token; the email comes from the token, not the body.
        String location = mockMvc.perform(post("/api/customers")
                        .with(bearer(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getHeader("Location");

        // Fetching the record requires the customer's own token.
        mockMvc.perform(get(location).with(bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Grace Hopper"));
    }

    @Test
    void registerCustomerWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        String email = "invalid-" + UUID.randomUUID() + "@example.com";
        // Blank full name is the only invalid field now (email comes from the token).
        String body = json(new CreateCustomerRequest(""));

        mockMvc.perform(post("/api/customers")
                        .with(bearer(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)));
    }

    @Test
    void openAccountForAuthenticatedCustomerReturns201() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        Customer customer = customerService.register("Acct Owner", email);
        String body = json(new OpenAccountRequest("USD"));

        mockMvc.perform(post("/api/accounts")
                        .with(bearer(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").isString())
                .andExpect(jsonPath("$.customerId").value(customer.getId().intValue()))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    // ---- money movement ------------------------------------------------

    @Test
    void depositUpdatesBalance() throws Exception {
        OwnedAccount owned = newAccount();

        mockMvc.perform(post("/api/accounts/{id}/deposits", owned.id())
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("150.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.primaryBalance").value(150.00))
                .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(get("/api/accounts/{id}/balance", owned.id())
                        .with(bearer(owned.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));
    }

    @Test
    void depositWithoutIdempotencyKeyReturns400() throws Exception {
        OwnedAccount owned = newAccount();

        mockMvc.perform(post("/api/accounts/{id}/deposits", owned.id())
                        .with(bearer(owned.email()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void replayingIdempotencyKeyDoesNotMoveMoneyTwice() throws Exception {
        OwnedAccount owned = newAccount();
        String key = UUID.randomUUID().toString();
        String body = json(new AmountRequest(new BigDecimal("40.00")));

        mockMvc.perform(post("/api/accounts/{id}/deposits", owned.id())
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(post("/api/accounts/{id}/deposits", owned.id())
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(get("/api/accounts/{id}/balance", owned.id())
                        .with(bearer(owned.email())))
                .andExpect(jsonPath("$.balance").value(40.00));
    }

    @Test
    void withdrawWithInsufficientFundsReturns422() throws Exception {
        OwnedAccount owned = newFundedAccount("20.00");

        mockMvc.perform(post("/api/accounts/{id}/withdrawals", owned.id())
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("50.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void depositToUnknownAccountReturns404() throws Exception {
        OwnedAccount owned = newAccount();

        // The account doesn't exist; the owner is authenticated but the guard hides it as 404.
        mockMvc.perform(post("/api/accounts/{id}/deposits", 999_999_999L)
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        OwnedAccount owned = newAccount();

        mockMvc.perform(post("/api/accounts/{id}/deposits", owned.id())
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AmountRequest(new BigDecimal("-5.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- transfer ------------------------------------------------------

    @Test
    void transferMovesMoneyBetweenAccounts() throws Exception {
        OwnedAccount source = newFundedAccount("100.00");
        OwnedAccount destination = newAccount();

        mockMvc.perform(post("/api/transfers")
                        .with(bearer(source.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(source.id(), destination.id(), new BigDecimal("30.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.primaryAccountId").value(source.id().intValue()))
                .andExpect(jsonPath("$.primaryBalance").value(70.00))
                .andExpect(jsonPath("$.counterAccountId").value(destination.id().intValue()))
                .andExpect(jsonPath("$.counterBalance").value(30.00));
    }

    @Test
    void transferToSameAccountReturns400() throws Exception {
        OwnedAccount owned = newFundedAccount("100.00");

        mockMvc.perform(post("/api/transfers")
                        .with(bearer(owned.email()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(owned.id(), owned.id(), new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- OpenAPI / Swagger --------------------------------------------

    @Test
    void openApiDocumentIsGeneratedAndPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Banking API"))
                .andExpect(jsonPath("$.paths['/api/transfers']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{id}/deposits']").exists());
    }

    // ---- history -------------------------------------------------------

    @Test
    void transactionHistoryListsEntriesNewestFirst() throws Exception {
        OwnedAccount owned = newAccount();
        moneyMovementService.deposit(owned.id(), new BigDecimal("10.00"), UUID.randomUUID().toString());
        moneyMovementService.withdraw(owned.id(), new BigDecimal("4.00"), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/accounts/{id}/transactions", owned.id())
                        .with(bearer(owned.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(6.00));
    }

    @Test
    void transactionHistoryIsPaginated() throws Exception {
        OwnedAccount owned = newAccount();
        // Five deposits -> five ledger entries on the customer account.
        for (int i = 0; i < 5; i++) {
            moneyMovementService.deposit(owned.id(), new BigDecimal("1.00"), UUID.randomUUID().toString());
        }

        // First page of 2 of 5: not the last page, exactly 2 items.
        mockMvc.perform(get("/api/accounts/{id}/transactions", owned.id())
                        .param("page", "0").param("size", "2")
                        .with(bearer(owned.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Last page holds the remaining single entry.
        mockMvc.perform(get("/api/accounts/{id}/transactions", owned.id())
                        .param("page", "2").param("size", "2")
                        .with(bearer(owned.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }
}
