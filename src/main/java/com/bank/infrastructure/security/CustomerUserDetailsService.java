package com.bank.infrastructure.security;

import com.bank.application.port.out.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Loads a customer by email (the login username) for HTTP Basic authentication.
 * Emails are stored lower-cased, so the supplied username is normalised the same way.
 */
@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = username == null ? "" : username.toLowerCase(Locale.ROOT);
        return customerRepository.findByEmail(normalizedEmail)
                .map(CustomerPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No customer for email"));
    }
}
