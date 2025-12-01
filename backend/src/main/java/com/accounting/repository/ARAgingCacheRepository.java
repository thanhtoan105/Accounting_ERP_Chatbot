package com.accounting.repository;

import com.accounting.entity.ARAgingCache;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ARAgingCache entity.
 * Provides data access methods for AR aging cache operations.
 */
@Repository
public interface ARAgingCacheRepository extends JpaRepository<ARAgingCache, UUID> {

    /**
     * Find aging cache entry for a specific company, customer, and snapshot date.
     *
     * @param companyId    company ID
     * @param customerId   customer ID
     * @param snapshotDate snapshot date
     * @return optional aging cache entry
     */
    Optional<ARAgingCache> findByCompanyIdAndCustomerIdAndSnapshotDate(
            Long companyId, Long customerId, LocalDate snapshotDate);

    /**
     * Find all aging cache entries for a company and snapshot date.
     *
     * @param companyId    company ID
     * @param snapshotDate snapshot date
     * @return list of aging cache entries
     */
    List<ARAgingCache> findByCompanyIdAndSnapshotDate(Long companyId, LocalDate snapshotDate);

    /**
     * Delete all aging cache entries for a company.
     * Used for cache invalidation.
     *
     * @param companyId company ID
     */
    void deleteByCompanyId(Long companyId);

    /**
     * Delete aging cache entries older than a specific date.
     * Used for cache cleanup.
     *
     * @param snapshotDate cutoff date
     */
    void deleteBySnapshotDateBefore(LocalDate snapshotDate);

    /**
     * Get total overdue amount for a company (sum of all overdue buckets).
     *
     * @param companyId    company ID
     * @param snapshotDate snapshot date
     * @return total overdue amount
     */
    @Query("SELECT COALESCE(SUM(c.days1To30 + c.days31To60 + c.days61To90 + c.daysOver90), 0) "
            + "FROM ARAgingCache c "
            + "WHERE c.companyId = :companyId AND c.snapshotDate = :snapshotDate")
    java.math.BigDecimal getTotalOverdueAmount(
            @Param("companyId") Long companyId, @Param("snapshotDate") LocalDate snapshotDate);
}
