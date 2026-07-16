package com.bank.application.port.out;

/**
 * Creates a login account in the external identity provider (Firebase). Used by an admin
 * to add staff (e.g. a new banker): the person can then sign in with the email/password
 * created here. Kept behind a port so the application code never imports the Firebase SDK
 * and can be tested with a fake.
 */
public interface UserProvisioner {

    /**
     * Create an email/password user in the identity provider.
     *
     * @return the provider's user id (uid)
     * @throws UserAlreadyExistsException if an account with this email already exists
     * @throws UserProvisioningException   on any other provisioning failure
     */
    String createUser(String email, String password, String displayName);

    /** Thrown when the email is already registered in the identity provider. */
    class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String email) {
            super("An identity-provider account already exists for " + email);
        }
    }

    /** Thrown when user provisioning fails for a reason other than a duplicate. */
    class UserProvisioningException extends RuntimeException {
        public UserProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
