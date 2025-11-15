package com.accounting.repository;

import com.accounting.entity.JournalEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for JournalEntry entities.
 * Journal entries are immutable after creation and are used for reporting.
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

  /**
   * Find all journal entries for a specific voucher.
   *
   * @param voucherId voucher ID
   * @return list of journal entries
   */
  List<JournalEntry> findByVoucherId(UUID voucherId);

  /**
   * Delete all journal entries for a specific voucher.
   * Used when unposting a voucher.
   *
   * @param voucherId voucher ID
   */
  void deleteByVoucherId(UUID voucherId);

  /**
   * Count journal entries for a specific voucher.
   *
   * @param voucherId voucher ID
   * @return count of journal entries
   */
  long countByVoucherId(UUID voucherId);

  /**
   * Find all journal entries for a specific period and account.
   * Used for reporting queries.
   *
   * @param periodId  period ID
   * @param accountId  account ID
   * @param companyId company ID
   * @return list of journal entries
   */
  @Query("SELECT je FROM JournalEntry je WHERE je.periodId = :periodId AND je.accountId = :accountId AND je.companyId = :companyId")
  List<JournalEntry> findByPeriodAndAccount(
      @Param("periodId") Long periodId,
      @Param("accountId") Long accountId,
      @Param("companyId") Long companyId);
}

