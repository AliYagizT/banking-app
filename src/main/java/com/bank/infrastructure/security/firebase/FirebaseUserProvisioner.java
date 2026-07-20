package com.bank.infrastructure.security.firebase;

import com.bank.application.port.out.UserProvisioner;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates Firebase email/password accounts via the Admin SDK (e.g. when an admin adds a
 * banker). Active only when Firebase is enabled; tests use a fake provisioner.
 */
@Component
@ConditionalOnProperty(prefix = "banking.firebase", name = "enabled", havingValue = "true")
public class FirebaseUserProvisioner implements UserProvisioner {

    private final FirebaseAuth firebaseAuth;

    public FirebaseUserProvisioner(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public String createUser(String email, String password, String displayName) {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setDisplayName(displayName)
                .setEmailVerified(true);
        try {
            return firebaseAuth.createUser(request).getUid();
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new UserAlreadyExistsException(email);
            }
            throw new UserProvisioningException("Could not create Firebase user for " + email, e);
        }
    }
}
