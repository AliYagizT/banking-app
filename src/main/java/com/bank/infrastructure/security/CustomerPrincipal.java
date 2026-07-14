package com.bank.infrastructure.security;

import com.bank.domain.model.Customer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated customer, exposed as the Spring Security principal. Carries the
 * customer's id so controllers can authorize access to accounts without an extra
 * database lookup (see {@code @AuthenticationPrincipal CustomerPrincipal}).
 */
public class CustomerPrincipal implements UserDetails {

    private final Long customerId;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final String role;

    public CustomerPrincipal(Customer customer) {
        this.customerId = customer.getId();
        this.email = customer.getEmail();
        this.passwordHash = customer.getPasswordHash();
        this.active = customer.isActive();
        this.role = customer.getRole().name();
    }

    public Long getCustomerId() {
        return customerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security's hasRole("ADMIN") checks for the "ROLE_ADMIN" authority.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // A closed customer cannot authenticate.
        return active;
    }
}
