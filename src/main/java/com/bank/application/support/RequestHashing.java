package com.bank.application.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces a stable digest of a money-movement request's parameters. Stored
 * alongside an idempotency key so that replaying the same key with a DIFFERENT
 * request (e.g. a different amount) can be detected and rejected, while a genuine
 * retry of the identical request is recognised and served from the stored result.
 */
public final class RequestHashing {

    private RequestHashing() {
    }

    /**
     * @param parts the canonical, order-significant components of the request
     *              (operation type, account ids, amount, ...)
     * @return a 64-character lowercase hex SHA-256 digest
     */
    public static String sha256Hex(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part).append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
