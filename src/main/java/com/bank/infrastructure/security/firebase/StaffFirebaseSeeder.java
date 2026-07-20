package com.bank.infrastructure.security.firebase;

import com.bank.application.port.out.CustomerRepository;
import com.bank.domain.CustomerRole;
import com.bank.domain.model.Customer;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-off provisioning: creates Firebase accounts for staff (BANKER/ADMIN) rows that exist
 * in the database (e.g. the seeded bankers) so they can sign in via Firebase like everyone
 * else. Runs only when {@code banking.firebase.seed-staff=true}; enable it once, start the
 * app, then turn it back off. Existing Firebase users are left untouched.
 *
 * <p>Requires Firebase to be enabled (it depends on {@link FirebaseAuth}).
 */
@Component
@ConditionalOnProperty(prefix = "banking.firebase", name = "seed-staff", havingValue = "true")
public class StaffFirebaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffFirebaseSeeder.class);

    private final FirebaseAuth firebaseAuth;
    private final CustomerRepository customerRepository;
    private final String staffPassword;

    public StaffFirebaseSeeder(FirebaseAuth firebaseAuth,
                               CustomerRepository customerRepository,
                               @Value("${banking.firebase.staff-password:banker123}") String staffPassword) {
        this.firebaseAuth = firebaseAuth;
        this.customerRepository = customerRepository;
        this.staffPassword = staffPassword;
    }

    @Override
    public void run(String... args) {
        provisionRole(CustomerRole.BANKER);
        provisionRole(CustomerRole.ADMIN);
    }

    private void provisionRole(CustomerRole role) {
        List<Long> ids = customerRepository.findIdsByRole(role);
        for (Long id : ids) {
            customerRepository.findById(id).ifPresent(this::provision);
        }
    }

    private void provision(Customer staff) {
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(staff.getEmail())
                .setPassword(staffPassword)
                .setDisplayName(staff.getFullName())
                .setEmailVerified(true);
        try {
            firebaseAuth.createUser(request);
            log.info("Firebase account created for staff {} <{}>", staff.getRole(), staff.getEmail());
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                log.info("Firebase account already exists for {}, skipping", staff.getEmail());
            } else {
                log.warn("Could not create Firebase account for {}: {}", staff.getEmail(), e.getMessage());
            }
        }
    }
}
