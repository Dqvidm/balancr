package service;

import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import com.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should reject transfer when Redis idempotency key is already present")
    void shouldRejectDuplicateTransactionWhenKeyExistsInRedis() {
        String referenceId = "REQ-IDEMP-001";

        when(valueOperations.setIfAbsent(eq("idempotency:" + referenceId), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                ledgerService.processTransfer("ACC-1", "ACC-2", BigDecimal.valueOf(50), referenceId)
        );

        assertTrue(exception.getMessage().contains("Duplicate transaction detected"));
        verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should evict key when an unexpected error occurs during processing")
    void shouldEvictKeyOnFailure() {
        String referenceId = "REQ-IDEMP-002";

        when(valueOperations.setIfAbsent(eq("idempotency:" + referenceId), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);
        when(transactionRepository.findByReferenceId(referenceId)).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberWithLock(anyString())).thenThrow(new RuntimeException("DB Failure"));

        assertThrows(RuntimeException.class, () ->
                ledgerService.processTransfer("ACC-1", "ACC-2", BigDecimal.valueOf(50), referenceId)
        );

        verify(redisTemplate).delete("idempotency:" + referenceId);
    }
}