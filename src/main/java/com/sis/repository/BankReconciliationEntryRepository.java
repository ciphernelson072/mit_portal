package com.sis.repository;

import com.sis.entity.BankReconciliationEntry;
import com.sis.entity.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BankReconciliationEntryRepository extends JpaRepository<BankReconciliationEntry, Long> {
    Optional<BankReconciliationEntry> findByProviderReference(String providerReference);
    List<BankReconciliationEntry> findByStatusOrderByStatementDateAsc(ReconciliationStatus status);
}