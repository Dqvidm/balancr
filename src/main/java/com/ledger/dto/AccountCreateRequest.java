package com.ledger.dto;

import com.ledger.domain.enums.AccountType;

public record AccountCreateRequest(
        String accountNumber,
        AccountType type
) {}