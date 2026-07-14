package com.bank.application.port.out;

/**
 * Output port for one-way password hashing, so the application core does not depend
 * on a specific crypto library (BCrypt lives in the infrastructure adapter).
 */
public interface PasswordHasher {

    String hash(String rawPassword);
}
