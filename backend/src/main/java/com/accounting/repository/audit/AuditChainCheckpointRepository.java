package com.accounting.repository.audit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.audit.AuditChainCheckpoint;

public interface AuditChainCheckpointRepository extends JpaRepository<AuditChainCheckpoint, Long> {

  Optional<AuditChainCheckpoint> findByCompanyIdAndEventDateUtc(
      Long companyId, LocalDate eventDateUtc);

  List<AuditChainCheckpoint> findByCompanyIdAndStatusOrderByEventDateUtcDesc(
      Long companyId, String status);

  List<AuditChainCheckpoint> findByCompanyIdAndEventDateUtcBetweenOrderByEventDateUtcAsc(
      Long companyId, LocalDate startDate, LocalDate endDate);
}
