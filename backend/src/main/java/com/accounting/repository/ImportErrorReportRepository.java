package com.accounting.repository;

import com.accounting.entity.ImportErrorReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportErrorReportRepository extends JpaRepository<ImportErrorReport, UUID> {

  Optional<ImportErrorReport> findByIdAndCompanyId(UUID id, Long companyId);
}

