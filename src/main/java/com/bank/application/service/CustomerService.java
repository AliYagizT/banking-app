package com.bank.application.service;

import com.bank.application.port.in.GetCustomerUseCase;
import com.bank.application.port.in.RegisterCustomerUseCase;
import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.PasswordHasher;
import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Customer;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomerService implements RegisterCustomerUseCase, GetCustomerUseCase {

    /** BCrypt truncates input beyond 72 bytes; reject longer passwords explicitly. */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final CustomerRepository customerRepository;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner transactionRunner;
    private final BankerAssignmentService bankerAssignmentService;

    public CustomerService(CustomerRepository customerRepository,
                           PasswordHasher passwordHasher,
                           TransactionRunner transactionRunner,
                           BankerAssignmentService bankerAssignmentService) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.transactionRunner = transactionRunner;
        this.bankerAssignmentService = bankerAssignmentService;
    }

    @Override
    public Customer register(String fullName, String email, String rawPassword) {
        String normalizedName = requireText(fullName, "fullName");
        // Normalise email with Locale.ROOT so case folding is stable regardless of
        // the server's default locale (e.g. the Turkish dotless-i would otherwise
        // corrupt addresses).
        String normalizedEmail = requireText(email, "email").toLowerCase(Locale.ROOT);
        String passwordHash = passwordHasher.hash(requireValidPassword(rawPassword));

        return transactionRunner.inNewTransaction(() -> {
            if (customerRepository.existsByEmail(normalizedEmail)) {
                throw new ValidationException("A customer with email '" + normalizedEmail + "' already exists");
            }
            Customer saved = customerRepository.save(new Customer(normalizedName, normalizedEmail, passwordHash));
            // Give every new customer a relationship banker (random) to own their future
            // credit applications. Harmless no-op if the bank has no bankers yet.
            bankerAssignmentService.getOrAssignBanker(saved.getId());
            return saved;
        });
    }

    @Override
    public Customer getCustomer(Long customerId) {
        return transactionRunner.inReadOnlyTransaction(() -> customerRepository.findById(customerId)
                .orElseThrow(() -> NotFoundException.customer(customerId)));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String requireValidPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("password must not be blank");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("password must be between " + MIN_PASSWORD_LENGTH
                    + " and " + MAX_PASSWORD_LENGTH + " characters");
        }
        return rawPassword;
    }
}
