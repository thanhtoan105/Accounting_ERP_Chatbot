package com.accounting.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.ImportAuditEntry;

public interface ImportAuditEntryRepository extends JpaRepository<ImportAuditEntry, UUID> {
    List<ImportAuditEntry> findByAttemptId(UUID attemptId);
}
