package com.bank.infrastructure.transaction;

import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.exception.ConcurrencyConflictException;
import com.bank.domain.exception.DuplicateIdempotencyKeyException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The Spring-based implementation of the {@link TransactionRunner} port. It owns the
 * transaction boundaries (a fresh {@code REQUIRES_NEW} transaction per write attempt,
 * a read-only one for queries) and — crucially — translates the persistence provider's
 * failures at commit time into domain exceptions, so the application core never sees a
 * Spring or Hibernate exception type:
 * <ul>
 *   <li>optimistic-lock failure → {@link ConcurrencyConflictException}</li>
 *   <li>idempotency-key unique violation → {@link DuplicateIdempotencyKeyException}</li>
 * </ul>
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.writeTransaction = new TransactionTemplate(transactionManager);
        // Each retry attempt must run in its own fresh transaction.
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public <T> T inNewTransaction(Supplier<T> work) {
        try {
            return writeTransaction.execute(status -> work.get());
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ConcurrencyConflictException("Account was modified concurrently");
        } catch (DataIntegrityViolationException e) {
            if (isIdempotencyKeyViolation(e)) {
                throw new DuplicateIdempotencyKeyException("Idempotency key was committed concurrently");
            }
            throw e;
        }
    }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return readTransaction.execute(status -> work.get());
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().toLowerCase(Locale.ROOT).contains("idempotency");
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("idempotency");
    }
}
