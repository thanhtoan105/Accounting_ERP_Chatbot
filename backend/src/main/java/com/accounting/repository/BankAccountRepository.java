package com.accounting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.BankAccount;

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

        /**
         * Find all active or inactive bank accounts for a company (for account picker).
         *
         * @param companyId company ID
         * @param active    active status filter
         * @return list of bank accounts matching the active status
         */
        List<BankAccount> findByCompanyIdAndActive(Long companyId, Boolean active);

        /**
         * Count unposted transactions referencing a bank account (for inactivation
         * check).
         * Checks voucher_lines where the voucher is not posted and references this
         * account's GL code.
         *
         * @param companyId     company ID
         * @param glAccountCode GL account code of the bank account
         * @return count of unposted transactions
         */
        @Query(value = "SELECT COUNT(*) FROM voucher_lines vl "
                        + "JOIN vouchers v ON vl.voucher_id = v.id "
                        + "JOIN chart_of_accounts coa ON vl.account_id = coa.id "
                        + "WHERE v.company_id = :companyId "
                        + "AND coa.code = :glAccountCode "
                        + "AND v.status != 'POSTED'", nativeQuery = true)
        long countUnpostedTransactionsByGlAccountCode(
                        @Param("companyId") Long companyId, @Param("glAccountCode") String glAccountCode);

        /**
         * Count posted voucher references to a bank account (for delete protection).
         * Checks voucher_lines where the voucher is posted and references this
         * account's GL code.
         *
         * @param companyId     company ID
         * @param glAccountCode GL account code of the bank account
         * @return count of posted references
         */
        @Query(value = "SELECT COUNT(*) FROM voucher_lines vl "
                        + "JOIN vouchers v ON vl.voucher_id = v.id "
                        + "JOIN chart_of_accounts coa ON vl.account_id = coa.id "
                        + "WHERE v.company_id = :companyId "
                        + "AND coa.code = :glAccountCode "
                        + "AND v.status = 'POSTED'", nativeQuery = true)
        long countPostedReferencesByGlAccountCode(
                        @Param("companyId") Long companyId, @Param("glAccountCode") String glAccountCode);

        /**
         * Get example unposted transactions for error message (for inactivation check).
         *
         * @param companyId     company ID
         * @param glAccountCode GL account code
         * @param limit         max number of examples to return
         * @return list of voucher numbers
         */
        @Query(value = "SELECT v.voucher_number FROM voucher_lines vl "
                        + "JOIN vouchers v ON vl.voucher_id = v.id "
                        + "JOIN chart_of_accounts coa ON vl.account_id = coa.id "
                        + "WHERE v.company_id = :companyId "
                        + "AND coa.code = :glAccountCode "
                        + "AND v.status != 'POSTED' "
                        + "LIMIT :limit", nativeQuery = true)
        List<String> findUnpostedTransactionExamples(
                        @Param("companyId") Long companyId,
                        @Param("glAccountCode") String glAccountCode,
                        @Param("limit") int limit);

        /**
         * Get example posted transactions for error message (for delete protection).
         *
         * @param companyId     company ID
         * @param glAccountCode GL account code
         * @param limit         max number of examples to return
         * @return list of voucher numbers
         */
        @Query(value = "SELECT v.voucher_number FROM voucher_lines vl "
                        + "JOIN vouchers v ON vl.voucher_id = v.id "
                        + "JOIN chart_of_accounts coa ON vl.account_id = coa.id "
                        + "WHERE v.company_id = :companyId "
                        + "AND coa.code = :glAccountCode "
                        + "AND v.status = 'POSTED' "
                        + "LIMIT :limit", nativeQuery = true)
        List<String> findPostedTransactionExamples(
                        @Param("companyId") Long companyId,
                        @Param("glAccountCode") String glAccountCode,
                        @Param("limit") int limit);

        /**
         * Get last transaction date for a bank account's GL code.
         *
         * @param companyId     company ID
         * @param glAccountCode GL account code
         * @return last voucher date or null if no transactions
         */
        @Query(value = "SELECT MAX(v.voucher_date) FROM voucher_lines vl "
                        + "JOIN vouchers v ON vl.voucher_id = v.id "
                        + "JOIN chart_of_accounts coa ON vl.account_id = coa.id "
                        + "WHERE v.company_id = :companyId "
                        + "AND coa.code = :glAccountCode "
                        + "AND v.status = 'POSTED'", nativeQuery = true)
        java.time.LocalDate findLastTransactionDate(
                        @Param("companyId") Long companyId, @Param("glAccountCode") String glAccountCode);
}
