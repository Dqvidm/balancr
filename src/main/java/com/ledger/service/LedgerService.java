package com.ledger.service;

import com.ledger.domain.enums.EntryDirection;
import com.ledger.domain.enums.TransactionStatus;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.LedgerEntry;
import com.ledger.domain.model.LedgerTransaction;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    public LedgerService(AccountRepository accountRepository,
                         LedgerTransactionRepository transactionRepository,
                         StringRedisTemplate redisTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
    }

    public Account createAccount(String accountNumber, com.ledger.domain.enums.AccountType type) {
        Account account = new Account(accountNumber, type);
        return accountRepository.save(account);
    }

    @Transactional
    public LedgerTransaction processTransfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey) {

        // 1. Enforce Distributed Idempotency via Redis (Lock key for 24 hours)
        String redisKey = "idempotency:" + idempotencyKey;
        Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNewRequest)) {
            throw new IllegalStateException("Duplicate request: Transaction with this idempotency key is already processed.");
        }

        // 2. Lock rows to prevent concurrent race conditions
        Account sourceAccount = accountRepository.findByIdForUpdate(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        Account targetAccount = accountRepository.findByIdForUpdate(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found"));

        // 3. Create transaction container
        LedgerTransaction transaction = new LedgerTransaction(idempotencyKey, "Account Transfer");

        // 4. Generate double-entry legs
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, amount, EntryDirection.DEBIT);
        LedgerEntry creditEntry = new LedgerEntry(targetAccount, amount, EntryDirection.CREDIT);
        // Update physical account balances
        sourceAccount.debit(amount);
        targetAccount.credit(amount);

        transaction.addEntry(debitEntry);
        transaction.addEntry(creditEntry);
        transaction.setStatus(TransactionStatus.POSTED);

        // 5. Persist the transaction and cascade the entries
        return transactionRepository.save(transaction);
    }
    public Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }
}