package service;

import com.ledger.BankerApplication;
import com.ledger.domain.enums.AccountType;
import com.ledger.domain.model.Account;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = BankerApplication.class)
@ActiveProfiles("test")
public class LedgerConcurrencyIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void whenConcurrentTransfers_thenBalancesRemainConsistentAndNoDoubleSpending() throws InterruptedException {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        Account source = ledgerService.createAccount("SRC-" + uniqueSuffix, AccountType.LIABILITY);
        Account target = ledgerService.createAccount("TGT-" + uniqueSuffix, AccountType.ASSET);

        source.credit(new BigDecimal("1000.00"));
        accountRepository.save(source);

        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                String idempotencyKey = UUID.randomUUID().toString();
                executor.submit(() -> {
                    try {
                        ledgerService.processTransfer(source.getId(), target.getId(), new BigDecimal("10.00"), idempotencyKey);
                        successfulTransfers.incrementAndGet();
                    } catch (Exception e) {
                        // Ignore exceptions for insufficient funds
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        Account finalSource = ledgerService.getAccount(source.getId());
        Account finalTarget = ledgerService.getAccount(target.getId());

        assertEquals(100, successfulTransfers.get());
        assertEquals(0, new BigDecimal("0.00").compareTo(finalSource.getBalance()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(finalTarget.getBalance()));
    }
}