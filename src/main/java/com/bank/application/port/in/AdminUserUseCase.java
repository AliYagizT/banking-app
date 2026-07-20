package com.bank.application.port.in;

import com.bank.domain.model.Customer;
import com.bank.domain.model.OperationLogEntry;

import java.util.List;

/**
 * Admin console operations for managing people and monitoring activity: list all users,
 * add a banker (identity-provider account + BANKER row), and read the recent audit log.
 * Restricted to the ADMIN role by {@code SecurityConfig}.
 */
public interface AdminUserUseCase {

    List<Customer> listUsers();

    Customer createBanker(CreateBankerCommand command);

    List<OperationLogEntry> recentOperations(int limit);
}
