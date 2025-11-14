package com.accounting.repository;

import com.accounting.entity.ImportAuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportAuditEntryRepository extends JpaRepository<ImportAuditEntry, UUID> {
    List<ImportAuditEntry> findByAttemptId(UUID attemptId);
}
