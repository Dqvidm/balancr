package com.ledger.domain.model;

import com.ledger.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
        // Default constructor required by JPA
    }

    public LedgerTransaction(String idempotencyKey, String description) {
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    public void addEntry(LedgerEntry entry) {
        this.entries.add(entry);
        entry.setTransaction(this);
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDescription() { return description; }
    public TransactionStatus getStatus() { return status; }
    public List<LedgerEntry> getEntries() { return entries; }
}