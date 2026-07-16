package com.bank;

import com.bank.support.TestSecurityConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests. Every test runs against a real PostgreSQL
 * started in a Docker container (never H2), with Flyway applying the production
 * migrations on startup.
 *
 * <p>Uses the <b>singleton container</b> pattern: the container is started once per
 * JVM in a static initializer and is intentionally never stopped between classes
 * (the Testcontainers reaper, Ryuk, removes it when the JVM exits). This matters
 * because Spring caches the application context across test classes; a per-class
 * {@code @Container}/{@code @Testcontainers} lifecycle would stop the container out
 * from under the cached datasource and break every test after the first class.
 *
 * <p>Requires a running Docker daemon; without one, Testcontainers fails fast.
 */
@SpringBootTest
@Import(TestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
