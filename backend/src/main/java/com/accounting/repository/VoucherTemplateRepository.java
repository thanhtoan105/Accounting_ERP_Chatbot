package com.accounting.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.VoucherTemplate;

public interface VoucherTemplateRepository extends JpaRepository<VoucherTemplate, UUID> {

  @EntityGraph(attributePaths = "lines")
  List<VoucherTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  @EntityGraph(attributePaths = "lines")
  List<VoucherTemplate> findByCompanyIdAndActiveOrderByCreatedAtDesc(Long companyId, boolean active);

  @EntityGraph(attributePaths = "lines")
  Optional<VoucherTemplate> findByCompanyIdAndId(Long companyId, UUID id);

  boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

  boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, UUID id);
}
