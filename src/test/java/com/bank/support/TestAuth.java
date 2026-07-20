package com.bank.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc helper for bearer authentication in tests: {@code .with(bearer(email))} adds an
 * {@code Authorization: Bearer <email>} header, which the {@link TestSecurityConfig} fake
 * verifier turns into a verified identity for that email.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor bearer(String email) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + email);
            return request;
        };
    }
}
