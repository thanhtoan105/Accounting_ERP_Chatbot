package com.accounting.repository.reconciliation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.reconciliation.BankStatementFormat;

/**
 * Repository for BankStatementFormat entities.
 * Provides persistence for column mapping profiles per bank account.
 */
public interface BankStatementFormatRepository extends JpaRepository<BankStatementFormat, UUID> {

    /**
     * Find format profile for a specific bank account.
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     * @return format profile if exists
     */
    Optional<BankStatementFormat> findByCompanyIdAndBankAccountId(Long companyId, Long bankAccountId);

    /**
     * Check if a format profile exists for a bank account.
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     * @return true if format exists
     */
    boolean existsByCompanyIdAndBankAccountId(Long companyId, Long bankAccountId);

    /**
     * Delete format profile for a bank account.
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     */
    void deleteByCompanyIdAndBankAccountId(Long companyId, Long bankAccountId);
}
