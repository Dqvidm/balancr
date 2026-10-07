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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    public LedgerService(AccountRepository accountRepository, LedgerTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
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

        if (transactionRepository.findByReferenceId(referenceId).isPresent()) {
            throw new IllegalStateException("Transaction reference already processed");
        }

        Account fromAccount = getAccount(fromAccountNumber);
        Account toAccount = getAccount(toAccountNumber);

        // Mutate account balances
        fromAccount.debit(amount);
        toAccount.credit(amount);

        // Persist updated account balances
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Record immutable ledger transaction and entries
        LedgerTransaction transaction = new LedgerTransaction(referenceId, TransactionStatus.POSTED.name());
        transaction.addEntry(new LedgerEntry(fromAccount, amount, EntryDirection.DEBIT));
        transaction.addEntry(new LedgerEntry(toAccount, amount, EntryDirection.CREDIT));

        transactionRepository.save(transaction);
    }
}