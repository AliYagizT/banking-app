package com.bank.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * HTTP Basic security. Customers authenticate with their email + password on every
 * request (stateless: no sessions, no CSRF tokens). Key protections wired here:
 * <ul>
 *   <li><b>Per-user credentials only</b> — there is no shared/embedded API key, so a
 *       browser or mobile client never has to carry a secret.</li>
 *   <li><b>Role-based access</b> — {@code /api/admin/**} requires the ADMIN role; a
 *       customer may otherwise only reach their own resources (enforced per-request by
 *       {@code AccountAccessGuard}).</li>
 *   <li><b>Brute-force guard</b> — a per-IP failed-login limiter runs before auth.</li>
 *   <li><b>CORS</b> — cross-origin requests are denied unless the origin is explicitly
 *       allow-listed via configuration.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final LoginAttemptService loginAttemptService;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          LoginAttemptService loginAttemptService,
                          ObjectMapper objectMapper,
                          @Value("${banking.security.cors.allowed-origins:}") String allowedOrigins) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.loginAttemptService = loginAttemptService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Stateless REST API authenticated per-request: CSRF tokens are not applicable.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Registration is open so a new customer can create an account to log in with.
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        // API documentation is public.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Administrative endpoints require the ADMIN role.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else requires an authenticated customer.
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Reject blocked IPs before their credentials are even checked.
                .addFilterBefore(new BruteForceGuardFilter(loginAttemptService, objectMapper),
                        BasicAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Cross-origin requests are denied by default; an operator opts specific browser
     * origins in via {@code banking.security.cors.allowed-origins} (comma-separated).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (!allowedOrigins.isBlank()) {
            config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
            config.setAllowCredentials(true);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
