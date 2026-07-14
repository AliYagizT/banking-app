package com.bank.application.service;

import com.bank.application.port.in.GetAccountUseCase;
import com.bank.application.port.in.ListAccountsUseCase;
import com.bank.application.port.in.OpenAccountUseCase;
import com.bank.application.port.out.AccountRepository;
import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Account;
import com.bank.domain.model.Customer;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService implements OpenAccountUseCase, GetAccountUseCase, ListAccountsUseCase {

    /**
     * The application currently operates in a single currency. The system
     * EXTERNAL-CASH counter-account is denominated in USD, so customer accounts
     * must match it. Multi-currency (one external account per currency) is out of
     * scope for now.
     */
    public static final String SUPPORTED_CURRENCY = "USD";

    private static final int ACCOUNT_NUMBER_DIGITS = 16;
    private static final int MAX_NUMBER_GENERATION_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRunner transactionRunner;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRunner transactionRunner) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public Account openAccount(Long customerId, String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        return transactionRunner.inNewTransaction(() -> {
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> NotFoundException.customer(customerId));
            if (!customer.isActive()) {
                throw new ValidationException("Customer " + customerId + " is not active and cannot open accounts");
            }
            Account account = new Account(generateUniqueAccountNumber(), customer, normalizedCurrency);
            return accountRepository.save(account);
        });
    }

    @Override
    public Account getAccount(Long accountId) {
        return transactionRunner.inReadOnlyTransaction(() -> accountRepository.findById(accountId)
                .orElseThrow(() -> NotFoundException.account(accountId)));
    }

    @Override
    public List<Account> listAccounts(Long customerId) {
        return transactionRunner.inReadOnlyTransaction(() ->
                accountRepository.findByCustomerId(customerId).stream()
                        // Defensive: the system EXTERNAL-CASH account has no owner, but never expose it.
                        .filter(account -> !account.isSystemAccount())
                        .toList());
    }

    private static String normalizeCurrency(String currency) {
        String normalized = (currency == null || currency.isBlank())
                ? SUPPORTED_CURRENCY
                : currency.strip().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCY.equals(normalized)) {
            throw new ValidationException("Unsupported currency '" + normalized
                    + "'. Only " + SUPPORTED_CURRENCY + " is supported");
        }
        return normalized;
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_NUMBER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomDigits(ACCOUNT_NUMBER_DIGITS);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely with 16 random digits; treated as a transient fault.
        throw new IllegalStateException("Could not generate a unique account number after "
                + MAX_NUMBER_GENERATION_ATTEMPTS + " attempts");
    }

    private String randomDigits(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }
}
