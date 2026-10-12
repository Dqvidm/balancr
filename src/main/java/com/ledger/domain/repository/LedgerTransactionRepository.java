package com.ledger.domain.repository;

import com.ledger.domain.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM LedgerTransaction t WHERE t.idempotencyKey = :referenceId")
    boolean existsByReferenceId(@Param("referenceId") String referenceId);

    @Query("SELECT t FROM LedgerTransaction t WHERE t.idempotencyKey = :referenceId")
    Optional<LedgerTransaction> findByReferenceId(@Param("referenceId") String referenceId);
}