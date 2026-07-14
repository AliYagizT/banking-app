package com.bank.adapter.in.web.dto;

import com.bank.domain.CustomerRole;
import com.bank.domain.CustomerStatus;
import com.bank.domain.model.Customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        CustomerStatus status,
        CustomerRole role,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getRole(),
                customer.getCreatedAt());
    }
}
