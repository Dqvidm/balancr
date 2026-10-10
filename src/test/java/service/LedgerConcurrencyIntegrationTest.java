package service;

import com.ledger.BankerApplication;
import com.ledger.domain.enums.AccountType;
import com.ledger.domain.model.Account;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BankerApplication.class)
class LedgerConcurrencyIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private String sourceAccountNumber;
    private String destinationAccountNumber;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        sourceAccountNumber = "SRC-" + UUID.randomUUID();
        destinationAccountNumber = "DST-" + UUID.randomUUID();

        Account source = ledgerService.createAccount(sourceAccountNumber, AccountType.ASSET);
        ledgerService.createAccount(destinationAccountNumber, AccountType.LIABILITY);

        source.credit(BigDecimal.valueOf(1000.00));
        accountRepository.save(source);
    }

    @Test
    @DisplayName("Should prevent double-spending and lost updates under high concurrency")
    void shouldHandleConcurrentTransfersSafely() throws InterruptedException {
        int numberOfThreads = 10;
        BigDecimal transferAmount = BigDecimal.valueOf(100.00);

        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try (ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads)) {
            for (int i = 0; i < numberOfThreads; i++) {
                executorService.submit(() -> {
                    try {
                        String referenceId = UUID.randomUUID().toString();
                        ledgerService.processTransfer(
                                sourceAccountNumber,
                                destinationAccountNumber,
                                transferAmount,
                                referenceId
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        Account finalSource = ledgerService.getAccount(sourceAccountNumber);
        Account finalDestination = ledgerService.getAccount(destinationAccountNumber);

        BigDecimal expectedDeduction = transferAmount.multiply(BigDecimal.valueOf(successCount.get()));

        assertEquals(
                BigDecimal.valueOf(1000.00).subtract(expectedDeduction).stripTrailingZeros(),
                finalSource.getBalance().stripTrailingZeros()
        );

        assertEquals(
                expectedDeduction.stripTrailingZeros(),
                finalDestination.getBalance().stripTrailingZeros()
        );
    }
}