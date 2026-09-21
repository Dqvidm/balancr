export interface Account {
    id: number;
    accountType: string;
    balance?: number;
}

export interface LedgerTransaction {
    id: number;
    status: string;
    amount: number;
    timestamp: string;
}

export interface TransferRequest {
    fromAccountId: number;
    toAccountId: number;
    amount: number;
}

export interface AccountCreateRequest {
    accountType: string;
    initialBalance: number;
}