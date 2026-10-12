package com.ledger.service;

import com.ledger.domain.enums.AccountType;
import com.ledger.domain.enums.EntryDirection;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.LedgerEntry;
import com.ledger.domain.model.LedgerTransaction;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import com.ledger.exception.ResourceNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class LedgerService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration LOCK_EXPIRATION = Duration.ofHours(24);

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    public LedgerService(
            AccountRepository accountRepository,
            LedgerTransactionRepository transactionRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Account createAccount(String accountNumber, AccountType type) {
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new IllegalStateException("Account already exists with number: " + accountNumber);
        }
        return accountRepository.save(new Account(accountNumber, type));
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    @Transactional
    public void processTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, String idempotencyKey) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly greater than zero");
        }
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", LOCK_EXPIRATION);

        if (!Boolean.TRUE.equals(isFirstRequest)) {
            throw new IllegalStateException("Duplicate transaction detected: Idempotency key already submitted or processed");
        }

        registerRedisTransactionSync(redisKey);

        try {
            if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
                throw new IllegalStateException("Transaction already persisted in database: " + idempotencyKey);
            }

            // lexicographical ordering prevents database deadlocks on cross transfers
            boolean lockOrder = fromAccountNumber.compareTo(toAccountNumber) < 0;
            String firstToLock = lockOrder ? fromAccountNumber : toAccountNumber;
            String secondToLock = lockOrder ? toAccountNumber : fromAccountNumber;

            Account lockedFirst = accountRepository.findByAccountNumberWithLock(firstToLock)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + firstToLock));
            Account lockedSecond = accountRepository.findByAccountNumberWithLock(secondToLock)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + secondToLock));

            Account fromAccount = fromAccountNumber.equals(lockedFirst.getAccountNumber()) ? lockedFirst : lockedSecond;
            Account toAccount = toAccountNumber.equals(lockedFirst.getAccountNumber()) ? lockedFirst : lockedSecond;

            fromAccount.debit(amount);
            toAccount.credit(amount);

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            String description = String.format("Transfer from %s to %s", fromAccountNumber, toAccountNumber);
            LedgerTransaction transaction = new LedgerTransaction(idempotencyKey, description);

            transaction.addEntry(new LedgerEntry(fromAccount, amount, EntryDirection.DEBIT));
            transaction.addEntry(new LedgerEntry(toAccount, amount, EntryDirection.CREDIT));

            transaction.markPosted();

            transactionRepository.save(transaction);

        } catch (Exception ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private void registerRedisTransactionSync(String redisKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.opsForValue().set(redisKey, "COMPLETED", LOCK_EXPIRATION);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        redisTemplate.delete(redisKey);
                    }
                }
            });
        }
    }
}