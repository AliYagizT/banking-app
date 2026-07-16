package com.bank.application.port.out;

import com.bank.application.model.VerifiedIdentity;

/**
 * Verifies a bearer token presented by a client and returns the proven identity. The
 * production implementation validates Firebase ID tokens; tests supply a fake. Keeping
 * this behind a port means the application/security code never imports the Firebase SDK
 * directly and can be exercised without a real Firebase project.
 */
public interface TokenVerifier {

    /**
     * @param token the raw bearer token (the value after "Bearer ")
     * @return the verified identity (uid + email)
     * @throws TokenVerificationException if the token is missing, malformed, expired or otherwise invalid
     */
    VerifiedIdentity verify(String token);

    /** Thrown when a bearer token cannot be verified. */
    class TokenVerificationException extends RuntimeException {
        public TokenVerificationException(String message, Throwable cause) {
            super(message, cause);
        }

        public TokenVerificationException(String message) {
            super(message);
        }
    }
}
