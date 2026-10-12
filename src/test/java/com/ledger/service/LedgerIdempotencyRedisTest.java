package com.ledger.service;

import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class LedgerIdempotencyRedisTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should reject transfer when Redis idempotency key is already present")
    void shouldRejectDuplicateTransactionWhenKeyExistsInRedis() {
        String referenceId = "REQ-IDEMP-001";

        Mockito.when(valueOperations.setIfAbsent(
                ArgumentMatchers.eq("idempotency:" + referenceId),
                ArgumentMatchers.eq("PROCESSING"),
                ArgumentMatchers.any(Duration.class)
        )).thenReturn(false);

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () ->
                ledgerService.processTransfer("ACC-1", "ACC-2", BigDecimal.valueOf(50), referenceId)
        );

        Assertions.assertTrue(exception.getMessage().contains("Duplicate transaction detected"));
        Mockito.verify(accountRepository, Mockito.never()).findByAccountNumberWithLock(ArgumentMatchers.anyString());
        Mockito.verify(transactionRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Should evict key when an unexpected error occurs during processing")
    void shouldEvictKeyOnFailure() {
        String referenceId = "REQ-IDEMP-002";

        Mockito.when(valueOperations.setIfAbsent(
                ArgumentMatchers.eq("idempotency:" + referenceId),
                ArgumentMatchers.eq("PROCESSING"),
                ArgumentMatchers.any(Duration.class)
        )).thenReturn(true);

        Mockito.when(accountRepository.findByAccountNumberWithLock(ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("DB Failure"));

        Assertions.assertThrows(RuntimeException.class, () ->
                ledgerService.processTransfer("ACC-1", "ACC-2", BigDecimal.valueOf(50), referenceId)
        );

        Mockito.verify(redisTemplate).delete("idempotency:" + referenceId);
    }
}