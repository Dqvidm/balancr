package com.ledger.service;

import com.ledger.domain.enums.AccountType;
import com.ledger.domain.enums.EntryDirection;
import com.ledger.domain.enums.TransactionStatus;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.LedgerEntry;
import com.ledger.domain.model.LedgerTransaction;
import com.ledger.domain.repository.AccountRepository;
import com.ledger.domain.repository.LedgerTransactionRepository;
import com.ledger.exception.ResourceNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class LedgerService {

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
            throw new RuntimeException("Account already exists");
        }
        Account account = new Account(accountNumber, type);
        return accountRepository.save(account);
    }

    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    @Transactional
    public void processTransfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, String referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct");
        }

        String redisKey = "idempotency:" + referenceId;
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isFirstRequest)) {
            throw new IllegalStateException("Duplicate transaction detected: Idempotency key already submitted or processed");
        }

        try {
            if (transactionRepository.findByReferenceId(referenceId).isPresent()) {
                throw new IllegalStateException("Transaction reference already processed in database");
            }

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

            // immutable double-entry persistence
            LedgerTransaction transaction = new LedgerTransaction(referenceId, TransactionStatus.POSTED.name());
            transaction.addEntry(new LedgerEntry(fromAccount, amount, EntryDirection.DEBIT));
            transaction.addEntry(new LedgerEntry(toAccount, amount, EntryDirection.CREDIT));

            transactionRepository.save(transaction);

            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofHours(24));

        } catch (Exception ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }
}