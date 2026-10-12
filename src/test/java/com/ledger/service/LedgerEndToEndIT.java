package com.ledger.service;

import com.ledger.AbstractIntegrationTest;
import com.ledger.domain.enums.AccountType;
import com.ledger.domain.model.Account;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEndToEndIT extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String sourceAccountNumber;
    private String targetAccountNumber;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        sourceAccountNumber = "E2E-SRC-" + UUID.randomUUID();
        targetAccountNumber = "E2E-DST-" + UUID.randomUUID();

        Account source = ledgerService.createAccount(sourceAccountNumber, AccountType.ASSET);
        ledgerService.createAccount(targetAccountNumber, AccountType.LIABILITY);

        source.credit(new BigDecimal("1000.0000"));
        accountRepository.save(source);
    }

    @Test
    @DisplayName("Should execute transfer in PostgreSQL and record lock in real Redis container")
    void shouldProcessTransferWithRealPostgresAndRedis() {
        String referenceId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("250.0000");

        ledgerService.processTransfer(sourceAccountNumber, targetAccountNumber, amount, referenceId);

        Account updatedSource = ledgerService.getAccount(sourceAccountNumber);
        Account updatedTarget = ledgerService.getAccount(targetAccountNumber);

        assertThat(updatedSource.getBalance()).isEqualByComparingTo(new BigDecimal("750.0000"));
        assertThat(updatedTarget.getBalance()).isEqualByComparingTo(new BigDecimal("250.0000"));

        // Verifies real Redis container has the idempotency key set
        Boolean hasKey = redisTemplate.hasKey("idempotency:" + referenceId);
        assertThat(hasKey).isTrue();
    }

    @Test
    @DisplayName("Should reject replay attacks using real Redis container key eviction")
    void shouldDropDuplicateRequestUsingRealRedis() {
        String referenceId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("100.0000");

        ledgerService.processTransfer(sourceAccountNumber, targetAccountNumber, amount, referenceId);

        assertThatThrownBy(() ->
                ledgerService.processTransfer(sourceAccountNumber, targetAccountNumber, amount, referenceId)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate transaction detected");

        Account updatedSource = ledgerService.getAccount(sourceAccountNumber);
        assertThat(updatedSource.getBalance()).isEqualByComparingTo(new BigDecimal("900.0000"));
    }
}