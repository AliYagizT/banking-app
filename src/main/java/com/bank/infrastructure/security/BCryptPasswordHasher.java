package com.bank.infrastructure.security;

import com.bank.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of the {@link PasswordHasher} port. It reuses the same
 * {@link PasswordEncoder} bean that Spring Security's authentication provider uses to
 * verify logins, so hashes produced here validate on sign-in.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
