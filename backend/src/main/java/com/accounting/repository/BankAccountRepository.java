package com.accounting.repository;

import com.accounting.entity.BankAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for BankAccount entities with company scoping and search
 * capabilities.
 */
public interface BankAccountRepository
        extends JpaRepository<BankAccount, Long>, JpaSpecificationExecutor<BankAccount> {

    /**
     * Find all bank accounts for a company.
     *
     * @param companyId company ID
     * @return list of bank accounts
     */
    List<BankAccount> findByCompanyId(Long companyId);

    /**
     * Find bank account by company and ID.
     *
     * @param companyId company ID
     * @param id        bank account ID
     * @return bank account or empty
     */
    Optional<BankAccount> findByCompanyIdAndId(Long companyId, Long id);

    /**
     * Check if bank account with account number exists for company (for duplicate
     * validation).
     *
     * @param companyId     company ID
     * @param accountNumber account number
     * @param excludeId     bank account ID to exclude (for updates)
     * @return true if account number exists, false otherwise
     */
    @Query("SELECT COUNT(b) > 0 FROM BankAccount b WHERE b.companyId = :companyId AND b.accountNumber = :accountNumber AND (:excludeId IS NULL OR b.id != :excludeId)")
    boolean existsByCompanyIdAndAccountNumber(
            @Param("companyId") Long companyId, @Param("accountNumber") String accountNumber,
            @Param("excludeId") Long excludeId);

    /**
     * Find bank account by company and account number.
     *
     * @param companyId     company ID
     * @param accountNumber account number
     * @return bank account or empty
     */
    Optional<BankAccount> findByCompanyIdAndAccountNumber(Long companyId, String accountNumber);

    /**
     * Search bank accounts by account number or bank name using native PostgreSQL
     * unaccent function.
     * Supports unaccented Vietnamese search (e.g., "viet" matches "việt").
     *
     * @param companyId  company ID
     * @param searchTerm search term (matches account number or unaccented bank
     *                   name)
     * @return list of matching bank accounts
     */
    @Query(value = "SELECT * FROM bank_accounts b "
            + "WHERE b.company_id = :companyId "
            + "AND ("
            + "  b.account_number ILIKE '%' || :searchTerm || '%' "
            + "  OR unaccent_search(b.bank_name) ILIKE '%' || unaccent_search(:searchTerm) || '%' "
            + ") "
            + "ORDER BY b.bank_name, b.account_number", nativeQuery = true)
    List<BankAccount> searchByAccountNumberOrBankNameNative(
            @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}
