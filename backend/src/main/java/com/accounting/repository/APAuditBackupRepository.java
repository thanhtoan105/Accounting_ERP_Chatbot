package com.accounting.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.accounting.entity.APAuditBackup;

public interface APAuditBackupRepository extends JpaRepository<APAuditBackup, Long>, JpaSpecificationExecutor<APAuditBackup> {
    
    List<APAuditBackup> findByCompanyIdOrderByBackupDateDesc(Long companyId);
    
    List<APAuditBackup> findByCompanyIdAndBackupDateBetweenOrderByBackupDateDesc(
        Long companyId, Instant startDate, Instant endDate);
}
