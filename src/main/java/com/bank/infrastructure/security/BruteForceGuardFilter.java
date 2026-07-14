package com.bank.infrastructure.security;

import com.bank.adapter.in.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP brute-force guard for HTTP Basic logins. Runs before the authentication filter:
 * a blocked IP is refused with HTTP 429 before its credentials are even checked. After
 * the request completes, the outcome updates the counter — a 401 (bad credentials) is a
 * failure; any other status for a request that carried credentials means the credentials
 * were accepted, which resets the counter.
 *
 * <p>Deriving the outcome from the response status (rather than Spring authentication
 * events) keeps this self-contained and independent of event-publisher wiring. Only
 * requests that actually carry an {@code Authorization} header are tracked, so anonymous
 * access to public endpoints is unaffected. Instantiated by {@code SecurityConfig} (not a
 * bean) so it is not double-registered in the main servlet filter chain.
 */
public class BruteForceGuardFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final ObjectMapper objectMapper;

    public BruteForceGuardFilter(LoginAttemptService loginAttemptService, ObjectMapper objectMapper) {
        this.loginAttemptService = loginAttemptService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isLoginAttempt = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        String ip = request.getRemoteAddr();

        if (isLoginAttempt && loginAttemptService.isBlocked(ip)) {
            writeTooManyRequests(request, response);
            return;
        }

        filterChain.doFilter(request, response);

        if (isLoginAttempt) {
            if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
                loginAttemptService.onFailure(ip);
            } else {
                // Credentials were accepted (2xx, or an authenticated 4xx like 403/404/409).
                loginAttemptService.onSuccess(ip);
            }
        }
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "TOO_MANY_ATTEMPTS", "Too many failed login attempts; please try again later",
                request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
