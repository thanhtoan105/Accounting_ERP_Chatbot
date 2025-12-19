package com.accounting.repository.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.ETLJobStatus;

@Repository
public interface DashboardETLRunRepository extends JpaRepository<DashboardETLRun, UUID> {

    @Query("SELECT e FROM DashboardETLRun e WHERE e.companyId = :companyId ORDER BY e.startedAt DESC LIMIT 1")
    Optional<DashboardETLRun> findLatestByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT e FROM DashboardETLRun e WHERE e.companyId = :companyId AND e.status = :status ORDER BY e.startedAt DESC LIMIT 1")
    Optional<DashboardETLRun> findLatestByCompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") ETLJobStatus status);

    @Query("SELECT e FROM DashboardETLRun e WHERE e.companyId = :companyId AND e.jobName = :jobName ORDER BY e.startedAt DESC LIMIT 1")
    Optional<DashboardETLRun> findLatestByCompanyIdAndJobName(
            @Param("companyId") Long companyId,
            @Param("jobName") String jobName);

    @Query("SELECT e FROM DashboardETLRun e WHERE e.companyId = :companyId ORDER BY e.startedAt DESC")
    List<DashboardETLRun> findRecentByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT e FROM DashboardETLRun e WHERE e.companyId = :companyId AND e.startedAt >= :since ORDER BY e.startedAt DESC")
    List<DashboardETLRun> findByCompanyIdSince(
            @Param("companyId") Long companyId,
            @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM DashboardETLRun e WHERE e.companyId = :companyId AND e.status = 'FAILED' AND e.startedAt >= :since")
    long countFailedSince(@Param("companyId") Long companyId, @Param("since") Instant since);

    @Query("SELECT e FROM DashboardETLRun e WHERE e.status = 'RUNNING' AND e.startedAt < :staleThreshold")
    List<DashboardETLRun> findStaleRunningJobs(@Param("staleThreshold") Instant staleThreshold);

    @Query("SELECT COUNT(e) FROM DashboardETLRun e WHERE (:companyId IS NULL OR e.companyId = :companyId) AND e.status = 'FAILED' AND e.startedAt >= :since")
    long countRecentFailedJobs(@Param("companyId") Long companyId, @Param("since") Instant since);
}
