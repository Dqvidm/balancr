package com.ledger.controller;

import com.ledger.domain.model.Account;
import com.ledger.dto.AccountCreateRequest;
import com.ledger.dto.TransferRequest;
import com.ledger.service.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<Account> createAccount(@RequestBody AccountCreateRequest request) {
        Account account = ledgerService.createAccount(request.accountNumber(), request.type());
        return ResponseEntity.ok(account);
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(ledgerService.getAccount(accountNumber));
    }

    @PostMapping("/transfers")
    public ResponseEntity<String> transfer(@RequestBody TransferRequest request) {
        ledgerService.processTransfer(
                request.fromAccountId().toString(),
                request.toAccountId().toString(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok("Transfer processed successfully");
    }
}