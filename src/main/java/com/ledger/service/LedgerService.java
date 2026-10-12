package com.ledger.service;

import com.ledger.domain.enums.AccountType;
import com.ledger.domain.enums.EntryDirection;
import com.ledger.domain.enums.TransactionStatus;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.LedgerEntry;
import com.ledger.domain.model.LedgerTransaction;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import com.ledger.exception.AccountAlreadyExistsException;
import com.ledger.exception.InvalidTransferException;
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
    private static final int MAX_ALLOWED_SCALE = 4;

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
            throw new AccountAlreadyExistsException("Account already exists with number: " + accountNumber);
        }
        return accountRepository.save(new Account(accountNumber, type));
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    @Transactional
    public void processTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, String referenceId) {
        validateTransferRequest(fromAccountNumber, toAccountNumber, amount, referenceId);

        String redisKey = IDEMPOTENCY_KEY_PREFIX + referenceId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", LOCK_EXPIRATION);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("Duplicate transaction detected: Idempotency key already submitted or processed");
        }

        // register post-commit/post-rollback hooks to ensure redis state matches db persistence
        registerTransactionSynchronization(redisKey);

        try {
            if (transactionRepository.findByReferenceId(referenceId).isPresent()) {
                throw new IllegalStateException("Transaction reference already persisted in database: " + referenceId);
            }

            // lexicographical sorting prevents db deadlocks under concurrent cross-transfers
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

            // double-entry record creation
            LedgerTransaction transaction = new LedgerTransaction(referenceId, TransactionStatus.POSTED.name());
            transaction.addEntry(new LedgerEntry(fromAccount, amount, EntryDirection.DEBIT));
            transaction.addEntry(new LedgerEntry(toAccount, amount, EntryDirection.CREDIT));

            transactionRepository.save(transaction);

        } catch (Exception ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private void validateTransferRequest(String fromAccount, String toAccount, BigDecimal amount, String referenceId) {
        if (referenceId == null || referenceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reference ID must not be blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be strictly greater than zero");
        }
        if (amount.scale() > MAX_ALLOWED_SCALE) {
            throw new InvalidTransferException("Transfer amount scale cannot exceed " + MAX_ALLOWED_SCALE + " decimal places");
        }
        if (fromAccount.equals(toAccount)) {
            throw new InvalidTransferException("Source and destination accounts must be distinct");
        }
    }

    private void registerTransactionSynchronization(String redisKey) {
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