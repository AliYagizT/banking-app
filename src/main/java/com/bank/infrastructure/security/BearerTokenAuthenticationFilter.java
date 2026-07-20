package com.bank.infrastructure.security;

import com.bank.application.model.VerifiedIdentity;
import com.bank.application.port.out.CustomerRepository;
import com.bank.application.port.out.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying an {@code Authorization: Bearer <idToken>} header. The
 * token is verified via {@link TokenVerifier} (Firebase in production); its email is then
 * matched to a customer row to establish the caller's identity and role. A verified caller
 * with no customer row yet becomes a {@code ROLE_PROSPECT} principal, allowed only to
 * register (create their profile).
 *
 * <p>Requests without a Bearer header proceed unauthenticated; the security entry point
 * then returns 401 for protected endpoints. An invalid/expired token is rejected with 401
 * immediately rather than being treated as anonymous. Instantiated by {@code SecurityConfig}
 * (not a bean) so it is not double-registered in the main servlet filter chain.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final CustomerRepository customerRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public BearerTokenAuthenticationFilter(TokenVerifier tokenVerifier,
                                           CustomerRepository customerRepository,
                                           RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenVerifier = tokenVerifier;
        this.customerRepository = customerRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // No bearer credentials: continue as anonymous (entry point 401s if protected).
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        VerifiedIdentity identity;
        try {
            identity = tokenVerifier.verify(token);
        } catch (TokenVerifier.TokenVerificationException e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Invalid bearer token", e));
            return;
        }

        // Roles/authorization come from the database, never from the token itself.
        CustomerPrincipal principal = customerRepository.findByEmail(identity.email())
                .map(CustomerPrincipal::of)
                .orElseGet(() -> CustomerPrincipal.prospect(identity.email()));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
