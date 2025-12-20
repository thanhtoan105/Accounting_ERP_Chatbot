package com.accounting.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.ImportErrorReport;

public interface ImportErrorReportRepository extends JpaRepository<ImportErrorReport, UUID> {

  Optional<ImportErrorReport> findByIdAndCompanyId(UUID id, Long companyId);
}
