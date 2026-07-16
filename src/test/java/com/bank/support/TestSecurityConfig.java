package com.bank.support;

import com.bank.application.model.VerifiedIdentity;
import com.bank.application.port.out.TokenVerifier;
import com.bank.application.port.out.UserProvisioner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Locale;
import java.util.UUID;

/**
 * Test wiring that replaces the real Firebase token verifier with a fake one, so
 * integration tests exercise the full bearer-auth path without a real Firebase project.
 *
 * <p>Convention: the bearer token <b>is</b> the caller's email. A blank token or the
 * literal {@code "invalid-token"} is rejected (to test 401 handling). The verified email
 * is then matched to a customer row exactly as in production.
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final String INVALID_TOKEN = "invalid-token";

    @Bean
    public TokenVerifier fakeTokenVerifier() {
        return token -> {
            if (token == null || token.isBlank() || token.equals(INVALID_TOKEN)) {
                throw new TokenVerifier.TokenVerificationException("fake verifier: invalid token");
            }
            String email = token.trim().toLowerCase(Locale.ROOT);
            return new VerifiedIdentity("test-uid:" + email, email);
        };
    }

    /** Fake identity-provider provisioning: no external call, just returns a synthetic uid. */
    @Bean
    public UserProvisioner fakeUserProvisioner() {
        return (email, password, displayName) -> "test-uid:" + UUID.randomUUID();
    }
}
