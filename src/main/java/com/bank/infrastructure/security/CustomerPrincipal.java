package com.bank.infrastructure.security;

import com.bank.domain.model.Customer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated caller, resolved from a verified bearer token and (usually) a matching
 * customer row. Exposed as the Spring Security principal so controllers can read the
 * caller's id/email/role via {@code @AuthenticationPrincipal CustomerPrincipal}.
 *
 * <p>A caller who has a valid token but no customer row yet is a <b>prospect</b>
 * ({@link #getCustomerId()} is {@code null}, role is {@code null}); they are granted only
 * {@code ROLE_PROSPECT}, which authorizes just the registration endpoint that creates
 * their profile.
 */
public class CustomerPrincipal {

    private static final String PROSPECT_ROLE = "PROSPECT";

    private final Long customerId; // null for a not-yet-registered (prospect) caller
    private final String email;
    private final String role;     // null for a prospect

    private CustomerPrincipal(Long customerId, String email, String role) {
        this.customerId = customerId;
        this.email = email;
        this.role = role;
    }

    /** Principal for a registered customer (carries id + role from the database). */
    public static CustomerPrincipal of(Customer customer) {
        return new CustomerPrincipal(customer.getId(), customer.getEmail(), customer.getRole().name());
    }

    /** Principal for a verified caller with no customer row yet (may only register). */
    public static CustomerPrincipal prospect(String email) {
        return new CustomerPrincipal(null, email, null);
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getEmail() {
        return email;
    }

    /** The customer's role name (CUSTOMER/BANKER/ADMIN), or {@code null} for a prospect. */
    public String getRole() {
        return role;
    }

    public boolean isRegistered() {
        return customerId != null;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security's hasRole("X") checks for the "ROLE_X" authority.
        String granted = role != null ? role : PROSPECT_ROLE;
        return List.of(new SimpleGrantedAuthority("ROLE_" + granted));
    }
}
