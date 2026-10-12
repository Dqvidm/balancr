package com.ledger.domain.repository;

import com.ledger.domain.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    default Optional<LedgerTransaction> findByReferenceId(String referenceId) {
        return findByIdempotencyKey(referenceId);
    }

    default boolean existsByReferenceId(String referenceId) {
        return existsByIdempotencyKey(referenceId);
    }
}