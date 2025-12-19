package com.accounting.repository.analytics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.analytics.AccountCategoryMapping;

public interface AccountCategoryMappingRepository extends JpaRepository<AccountCategoryMapping, UUID> {

    @Query("""
            SELECT m FROM AccountCategoryMapping m
            WHERE m.active = true
            AND (m.companyId = :companyId OR m.companyId IS NULL)
            ORDER BY m.companyId NULLS LAST, m.accountCodePrefix
            """)
    List<AccountCategoryMapping> findByCompanyIdOrGlobal(@Param("companyId") Long companyId);

    @Query("""
            SELECT m FROM AccountCategoryMapping m
            WHERE m.active = true
            AND m.accountCodePrefix = :prefix
            AND (m.companyId = :companyId OR m.companyId IS NULL)
            ORDER BY m.companyId NULLS LAST
            LIMIT 1
            """)
    Optional<AccountCategoryMapping> findByAccountCodePrefixAndCompanyId(
            @Param("prefix") String prefix,
            @Param("companyId") Long companyId);

    @Query("""
            SELECT m FROM AccountCategoryMapping m
            WHERE m.active = true
            AND m.isGlobal = true
            AND m.companyId IS NULL
            ORDER BY m.accountCodePrefix
            """)
    List<AccountCategoryMapping> findGlobalMappings();

    List<AccountCategoryMapping> findByCompanyIdAndActiveTrue(Long companyId);
}
