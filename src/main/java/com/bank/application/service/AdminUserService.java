package com.bank.application.service;

import com.bank.application.port.in.AdminUserUseCase;
import com.bank.application.port.in.CreateBankerCommand;
import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.OperationLogRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.application.port.out.UserProvisioner;
import com.bank.domain.CustomerRole;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Customer;
import com.bank.domain.model.OperationLogEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Admin console service. Adding a banker first creates the login account in the identity
 * provider (Firebase) and then the {@code BANKER} customer row, so the new banker can sign
 * in immediately and enters the pool that new customers are randomly assigned to.
 */
@Service
public class AdminUserService implements AdminUserUseCase {

    /** Firebase requires passwords of at least 6 characters. */
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_RECENT_OPERATIONS = 500;

    private final CustomerRepository customerRepository;
    private final OperationLogRepository operationLogRepository;
    private final UserProvisioner userProvisioner;
    private final TransactionRunner transactionRunner;

    public AdminUserService(CustomerRepository customerRepository,
                            OperationLogRepository operationLogRepository,
                            UserProvisioner userProvisioner,
                            TransactionRunner transactionRunner) {
        this.customerRepository = customerRepository;
        this.operationLogRepository = operationLogRepository;
        this.userProvisioner = userProvisioner;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public List<Customer> listUsers() {
        return transactionRunner.inReadOnlyTransaction(customerRepository::findAllNewestFirst);
    }

    @Override
    public Customer createBanker(CreateBankerCommand command) {
        String fullName = requireText(command.fullName(), "fullName");
        String email = requireText(command.email(), "email").toLowerCase(Locale.ROOT);
        String password = command.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        // Reject duplicates up-front so we don't create an orphan identity-provider account.
        if (transactionRunner.inReadOnlyTransaction(() -> customerRepository.existsByEmail(email))) {
            throw new ValidationException("A user with email '" + email + "' already exists");
        }

        // Create the login account first; a duplicate here surfaces as a validation error.
        try {
            userProvisioner.createUser(email, password, fullName);
        } catch (UserProvisioner.UserAlreadyExistsException e) {
            throw new ValidationException("An identity-provider account already exists for '" + email + "'");
        }

        return transactionRunner.inNewTransaction(() -> {
            Customer banker = new Customer(fullName, email);
            banker.setRole(CustomerRole.BANKER);
            return customerRepository.save(banker);
        });
    }

    @Override
    public List<OperationLogEntry> recentOperations(int limit) {
        int capped = Math.min(Math.max(1, limit), MAX_RECENT_OPERATIONS);
        return transactionRunner.inReadOnlyTransaction(() -> operationLogRepository.findRecent(capped));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
        return value.strip();
    }
}
