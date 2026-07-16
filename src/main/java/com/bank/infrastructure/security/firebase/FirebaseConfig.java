package com.bank.infrastructure.security.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Initializes the Firebase Admin SDK, used to verify Firebase ID tokens (Bearer auth) and
 * to provision staff accounts. Active only when {@code banking.firebase.enabled=true}, so
 * integration tests (which supply a fake verifier and do not set the flag) never touch a
 * real Firebase project.
 *
 * <p>Credentials are loaded from {@code banking.firebase.credentials-path} if set,
 * otherwise from the ambient Application Default Credentials
 * ({@code GOOGLE_APPLICATION_CREDENTIALS}). The service-account key is a secret and is
 * never committed to the repository.
 */
@Configuration
@ConditionalOnProperty(prefix = "banking.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(@Value("${banking.firebase.credentials-path:}") String credentialsPath,
                                   @Value("${banking.firebase.project-id:}") String projectId) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(in);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }
        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId);
        }
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
