package com.accounting.repository.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.analytics.AnalyticsAuditLog;

@Repository
public interface AnalyticsAuditLogRepository extends JpaRepository<AnalyticsAuditLog, UUID> {

    Optional<AnalyticsAuditLog> findTopByCompanyIdOrderBySequenceInCompanyDesc(Long companyId);

    @Query("SELECT MAX(a.sequenceInDay) FROM AnalyticsAuditLog a WHERE a.companyId = :companyId AND a.eventDateUtc = :eventDateUtc")
    Optional<Long> findMaxSequenceInDayByCompanyIdAndEventDateUtc(
            @Param("companyId") Long companyId,
            @Param("eventDateUtc") LocalDate eventDateUtc);

    @Query("SELECT MAX(a.sequenceInCompany) FROM AnalyticsAuditLog a WHERE a.companyId = :companyId")
    Optional<Long> findMaxSequenceInCompanyByCompanyId(@Param("companyId") Long companyId);

    List<AnalyticsAuditLog> findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(
            Long companyId, LocalDate eventDateUtc);

    List<AnalyticsAuditLog> findByCompanyIdAndEventDateUtcBetweenOrderBySequenceInCompanyAsc(
            Long companyId, LocalDate startDate, LocalDate endDate);

    long countByCompanyIdAndEventDateUtc(Long companyId, LocalDate eventDateUtc);

    Optional<AnalyticsAuditLog> findByCompanyIdAndSequenceInCompany(
            Long companyId, Long sequenceInCompany);
}
