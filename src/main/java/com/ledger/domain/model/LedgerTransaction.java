package com.ledger.domain.model;

import com.ledger.domain.enums.EntryDirection;
import com.ledger.domain.enums.TransactionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "ledger_transactions",
        indexes = {
                @Index(name = "idx_ledger_transactions_idempotency_key", columnList = "idempotency_key", unique = true)
        }
)
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransactionStatus status;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<LedgerEntry> entries = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerTransaction() {
        // required JPA
    }

    public LedgerTransaction(String idempotencyKey, String description) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = TransactionStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addEntry(LedgerEntry entry) {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot add entries to a transaction in status: " + this.status);
        }
        Objects.requireNonNull(entry, "Ledger entry must not be null");
        this.entries.add(entry);
        entry.setTransaction(this);
    }

    public void markPosted() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be marked as POSTED");
        }
        if (this.entries.size() < 2) {
            throw new IllegalStateException("A double-entry transaction requires at least two balanced entries");
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (LedgerEntry entry : this.entries) {
            if (entry.getDirection() == EntryDirection.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getDirection() == EntryDirection.CREDIT) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                    String.format("Unbalanced transaction: Debits (%s) do not equal Credits (%s)", totalDebits, totalCredits)
            );
        }

        this.status = TransactionStatus.POSTED;
    }

    public void markRejected() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be marked as REJECTED");
        }
        this.status = TransactionStatus.REJECTED;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}