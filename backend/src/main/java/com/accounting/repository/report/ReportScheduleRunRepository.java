package com.accounting.repository.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.report.ReportScheduleRun;
import com.accounting.entity.report.RunStatus;

@Repository
public interface ReportScheduleRunRepository extends JpaRepository<ReportScheduleRun, UUID> {

  Optional<ReportScheduleRun> findByIdAndCompanyId(UUID id, Long companyId);

  Page<ReportScheduleRun> findByScheduleIdAndCompanyIdOrderByQueuedAtDesc(
      UUID scheduleId, Long companyId, Pageable pageable);

  @Query("SELECT r FROM ReportScheduleRun r WHERE r.companyId = :companyId "
      + "AND r.scheduleId = :scheduleId AND r.periodId = :periodId AND r.status IN :statuses")
  Optional<ReportScheduleRun> findExistingRun(
      @Param("companyId") Long companyId,
      @Param("scheduleId") UUID scheduleId,
      @Param("periodId") UUID periodId,
      @Param("statuses") List<RunStatus> statuses);

  @Query("SELECT r FROM ReportScheduleRun r WHERE r.companyId = :companyId "
      + "AND r.status IN ('PENDING', 'RUNNING') ORDER BY r.queuedAt ASC")
  List<ReportScheduleRun> findUpcomingRuns(@Param("companyId") Long companyId);

  @Query("SELECT r FROM ReportScheduleRun r WHERE r.companyId = :companyId ORDER BY r.queuedAt DESC")
  Page<ReportScheduleRun> findHistoricalRuns(@Param("companyId") Long companyId, Pageable pageable);

  Page<ReportScheduleRun> findByCompanyIdOrderByQueuedAtDesc(Long companyId, Pageable pageable);

  @Query("SELECT r FROM ReportScheduleRun r WHERE r.companyId = :companyId "
      + "AND r.status = 'FAILED' AND r.attempt < r.maxAttempts")
  List<ReportScheduleRun> findRetryableRuns(@Param("companyId") Long companyId);

  @Query("SELECT COUNT(r) FROM ReportScheduleRun r WHERE r.scheduleId = :scheduleId AND r.status = :status")
  Long countByScheduleIdAndStatus(@Param("scheduleId") UUID scheduleId, @Param("status") RunStatus status);
}
