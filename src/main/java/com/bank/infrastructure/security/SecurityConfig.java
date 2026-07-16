package com.bank.infrastructure.security;

import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.TokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless, token-based security. Clients authenticate with a Firebase ID token in an
 * {@code Authorization: Bearer <token>} header; {@link BearerTokenAuthenticationFilter}
 * verifies it and resolves the caller's identity and role from the database. Key points:
 * <ul>
 *   <li><b>No passwords in the backend</b> — Firebase owns credentials; the app only
 *       verifies tokens and maps the email to a customer row.</li>
 *   <li><b>Role-based access</b> — {@code /api/admin/**} needs ADMIN, {@code /api/banker/**}
 *       needs BANKER; other endpoints need a registered customer. Per-resource ownership is
 *       enforced in the service layer (account/banker access guards).</li>
 *   <li><b>Registration</b> — {@code POST /api/customers} only needs a verified token (a
 *       not-yet-registered "prospect"), so a signed-in Firebase user can create their
 *       profile; the email comes from the token, never the request body.</li>
 *   <li><b>CORS</b> — cross-origin requests are denied unless the origin is allow-listed.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final TokenVerifier tokenVerifier;
    private final CustomerRepository customerRepository;
    private final String allowedOrigins;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          TokenVerifier tokenVerifier,
                          CustomerRepository customerRepository,
                          @Value("${banking.security.cors.allowed-origins:}") String allowedOrigins) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.tokenVerifier = tokenVerifier;
        this.customerRepository = customerRepository;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Stateless REST API authenticated per-request by bearer token: CSRF N/A.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // API documentation is public.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Registration: a verified (possibly not-yet-registered) caller creates their profile.
                        .requestMatchers(HttpMethod.POST, "/api/customers").authenticated()
                        // Administrative endpoints require the ADMIN role.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Banker (credit evaluation) endpoints require the BANKER role.
                        .requestMatchers("/api/banker/**").hasRole("BANKER")
                        // Everything else requires a registered customer (not a bare prospect).
                        .anyRequest().hasAnyRole("CUSTOMER", "BANKER", "ADMIN"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new BearerTokenAuthenticationFilter(tokenVerifier, customerRepository, authenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class);
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
