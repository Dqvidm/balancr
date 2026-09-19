package com.ledger.controller;

import com.ledger.domain.model.Account;
import com.ledger.domain.model.LedgerTransaction;
import com.ledger.dto.AccountCreateRequest;
import com.ledger.dto.TransferRequest;
import com.ledger.service.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
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

    @PostMapping("/transfers")
    public ResponseEntity<LedgerTransaction> processTransfer(@RequestBody TransferRequest request) {
        LedgerTransaction transaction = ledgerService.processTransfer(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(transaction);
    }
    @GetMapping("/accounts/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ledgerService.getAccount(id));
    }
}