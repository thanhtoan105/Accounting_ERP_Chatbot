package com.accounting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.ChartOfAccount;

public interface ChartOfAccountsRepository
                extends JpaRepository<ChartOfAccount, Long>, JpaSpecificationExecutor<ChartOfAccount> {

        /**
         * Find all accounts for a company.
         *
         * @param companyId company ID
         * @return list of accounts
         */
        List<ChartOfAccount> findByCompanyId(Long companyId);

        /**
         * Find accounts by company and code prefix.
         *
         * @param companyId  company ID
         * @param codePrefix code prefix (e.g., "131")
         * @return list of accounts matching the prefix
         */
        List<ChartOfAccount> findByCompanyIdAndCodeStartingWith(Long companyId, String codePrefix);

        /**
         * Find accounts by company and parent ID.
         *
         * @param companyId company ID
         * @param parentId  parent account ID (null for root accounts)
         * @return list of child accounts
         */
        List<ChartOfAccount> findByCompanyIdAndParentId(Long companyId, Long parentId);

        /**
         * Find postable accounts (leaf accounts with postable=true) for a company.
         *
         * @param companyId company ID
         * @return list of postable accounts
         */
        List<ChartOfAccount> findByCompanyIdAndPostableTrue(Long companyId);

        /**
         * Check if account has children (is not a leaf).
         *
         * @param accountId account ID
         * @return true if account has children, false otherwise
         */
        @Query("SELECT COUNT(c) > 0 FROM ChartOfAccount c WHERE c.parentId = :accountId")
        boolean hasChildren(@Param("accountId") Long accountId);

        /**
         * Find accounts by company and account type.
         *
         * @param companyId company ID
         * @param type      account type (Asset, Liability, etc.)
         * @return list of accounts of the specified type
         */
        List<ChartOfAccount> findByCompanyIdAndType(Long companyId, String type);

        /**
         * Check if account code exists for company (for duplicate validation).
         *
         * @param companyId company ID
         * @param code      account code
         * @param excludeId account ID to exclude (for updates)
         * @return true if code exists, false otherwise
         */
        @Query("SELECT COUNT(c) > 0 FROM ChartOfAccount c WHERE c.companyId = :companyId AND c.code = :code AND (:excludeId IS NULL OR c.id != :excludeId)")
        boolean existsByCompanyIdAndCode(
                        @Param("companyId") Long companyId, @Param("code") String code,
                        @Param("excludeId") Long excludeId);

        /**
         * Find account by company and exact code.
         *
         * @param companyId company ID
         * @param code      account code
         * @return account if present
         */
        java.util.Optional<ChartOfAccount> findByCompanyIdAndCode(Long companyId, String code);

        /**
         * Find account by ID scoped to company.
         *
         * @param id account ID
         * @param companyId company ID
         * @return account if present
         */
        java.util.Optional<ChartOfAccount> findByIdAndCompanyId(Long id, Long companyId);

        /**
         * Search accounts by code or name using native PostgreSQL unaccent function.
         * Supports unaccented Vietnamese search (e.g., "nha" matches "nhà").
         *
         * @param companyId  company ID
         * @param searchTerm search term (matches code or unaccented name)
         * @return list of matching accounts
         */
        @Query(value = "SELECT * FROM chart_of_accounts c "
                        + "WHERE c.company_id = :companyId "
                        + "AND ("
                        + "  c.code ILIKE '%' || :searchTerm || '%' "
                        + "  OR unaccent_search(c.name) ILIKE '%' || unaccent_search(:searchTerm) || '%' "
                        + ") "
                        + "ORDER BY c.ordering_position, c.code", nativeQuery = true)
        List<ChartOfAccount> searchByCodeOrNameNative(
                        @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);

        /**
         * Delete all accounts for a company using native query.
         * This handles the parent-child FK constraint by first setting parent_id to
         * NULL,
         * then deleting all accounts.
         *
         * @param companyId company ID
         */
        @org.springframework.data.jpa.repository.Modifying
        @Query(value = "UPDATE chart_of_accounts SET parent_id = NULL WHERE company_id = :companyId; "
                        + "DELETE FROM chart_of_accounts WHERE company_id = :companyId", nativeQuery = true)
        void deleteAllByCompanyId(@Param("companyId") Long companyId);
}
