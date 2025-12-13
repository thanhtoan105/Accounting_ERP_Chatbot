package com.accounting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.TenantProvision;

public interface TenantProvisionRepository extends JpaRepository<TenantProvision, Long> {
    List<TenantProvision> findByCompanyId(Long companyId);

    List<TenantProvision> findByCreatedBy(Long createdBy);
}
