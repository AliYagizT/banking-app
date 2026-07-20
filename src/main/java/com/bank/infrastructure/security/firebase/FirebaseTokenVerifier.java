package com.bank.infrastructure.security.firebase;

import com.bank.application.model.VerifiedIdentity;
import com.bank.application.port.out.TokenVerifier;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Verifies Firebase ID tokens with the Admin SDK. Active only when Firebase is enabled;
 * tests supply a {@code TokenVerifier} fake instead. Emails are lower-cased with
 * {@link Locale#ROOT} to match how customer emails are normalized on registration.
 */
@Component
@ConditionalOnProperty(prefix = "banking.firebase", name = "enabled", havingValue = "true")
public class FirebaseTokenVerifier implements TokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public VerifiedIdentity verify(String token) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(token);
            String email = decoded.getEmail();
            if (email == null || email.isBlank()) {
                throw new TokenVerificationException("Verified token carries no email address");
            }
            return new VerifiedIdentity(decoded.getUid(), email.toLowerCase(Locale.ROOT));
        } catch (FirebaseAuthException e) {
            throw new TokenVerificationException("Invalid or expired Firebase ID token", e);
        }
    }
}
