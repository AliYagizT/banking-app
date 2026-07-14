package com.bank.adapter.in.web;

import com.bank.adapter.in.web.dto.ErrorResponse;
import com.bank.domain.exception.AccountNotActiveException;
import com.bank.domain.exception.BankingException;
import com.bank.domain.exception.ConcurrencyConflictException;
import com.bank.domain.exception.DuplicateIdempotencyKeyException;
import com.bank.domain.exception.IdempotencyConflictException;
import com.bank.domain.exception.InsufficientFundsException;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Translates exceptions into the single {@link ErrorResponse} JSON shape with an
 * appropriate HTTP status and a stable machine-readable {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Domain exceptions --------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", e.getMessage(), request);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e,
                                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE", e.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException e,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", e.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), request);
    }

    // ---- Concurrency --------------------------------------------------

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyConflictException e,
                                                           HttpServletRequest request) {
        // Retries were exhausted under contention; the client may safely retry.
        return build(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT",
                "The operation could not complete due to concurrent activity; please retry", request);
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateIdempotencyKeyException e,
                                                            HttpServletRequest request) {
        // Normally consumed internally (the stored result is returned); mapped here for safety.
        return build(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", e.getMessage(), request);
    }

    /** Fallback for any other domain exception. */
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBanking(BankingException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
    }

    // ---- Request binding / validation ---------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "VALIDATION_ERROR", "Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header '" + e.getHeaderName() + "' is missing", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Parameter '" + e.getName() + "' has an invalid value", request);
    }

    // ---- Catch-all ----------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        // Log the full detail server-side; never leak internals to the client.
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
