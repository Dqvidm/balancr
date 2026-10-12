package com.ledger.controller;

import com.ledger.domain.model.Account;
import com.ledger.dto.AccountCreateRequest;
import com.ledger.dto.TransferRequest;
import com.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        Account account = ledgerService.createAccount(request.accountNumber(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(ledgerService.getAccount(accountNumber));
    }

    @PostMapping("/transfers")
    public ResponseEntity<String> transfer(@Valid @RequestBody TransferRequest request) {
        ledgerService.processTransfer(
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok("Transfer processed successfully");
    }
}