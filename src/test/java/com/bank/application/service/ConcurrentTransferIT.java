package com.bank.application.service;

import com.bank.AbstractIntegrationTest;
import com.bank.adapter.out.persistence.LedgerEntryJpaRepository;
import com.bank.domain.model.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that optimistic locking + bounded retry prevents the lost-update problem:
 * many transactions hammering the same account row must still leave an exact balance.
 */
class ConcurrentTransferIT extends AbstractIntegrationTest {

    @Autowired
    private MoneyMovementService moneyMovementService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerEntryJpaRepository ledgerEntryRepository;

    private Account newAccount() {
        var customer = customerService.register(
                "Conc User", "conc-" + UUID.randomUUID() + "@example.com");
        return accountService.openAccount(customer.getId(), "USD");
    }

    @Test
    void concurrentTransfersAgainstHotAccountsKeepExactBalances() throws InterruptedException {
        int threads = 20;
        BigDecimal perTransfer = new BigDecimal("1.00");

        Account source = newAccount();
        moneyMovementService.deposit(source.getId(), new BigDecimal("1000.00"), UUID.randomUUID().toString());
        Account destination = newAccount();

        List<Throwable> failures = runConcurrently(threads, () ->
                moneyMovementService.transfer(
                        source.getId(), destination.getId(), perTransfer, UUID.randomUUID().toString()));

        assertThat(failures).as("no transfer should fail").isEmpty();

        BigDecimal moved = perTransfer.multiply(BigDecimal.valueOf(threads));
        assertThat(moneyMovementService.getBalance(source.getId()).balance())
                .isEqualByComparingTo(new BigDecimal("1000.00").subtract(moved));
        assertThat(moneyMovementService.getBalance(destination.getId()).balance())
                .isEqualByComparingTo(moved);

        // Cached balances still agree with the immutable ledger.
        assertThat(ledgerEntryRepository.sumSignedAmountByAccountId(destination.getId()))
                .isEqualByComparingTo(moved);
    }

    @Test
    void concurrentDepositsToSameAccountAreAllApplied() throws InterruptedException {
        int threads = 15;
        BigDecimal perDeposit = new BigDecimal("10.00");
        Account account = newAccount();

        List<Throwable> failures = runConcurrently(threads, () ->
                moneyMovementService.deposit(account.getId(), perDeposit, UUID.randomUUID().toString()));

        assertThat(failures).isEmpty();
        assertThat(moneyMovementService.getBalance(account.getId()).balance())
                .isEqualByComparingTo(perDeposit.multiply(BigDecimal.valueOf(threads)));
    }

    /**
     * Runs {@code task} on {@code threadCount} threads released simultaneously, and
     * returns any throwables they raised.
     */
    private List<Throwable> runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    task.run();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads at once for maximum contention
        assertThat(doneGate.await(60, TimeUnit.SECONDS)).as("all tasks finished in time").isTrue();
        pool.shutdownNow();
        return failures;
    }
}
