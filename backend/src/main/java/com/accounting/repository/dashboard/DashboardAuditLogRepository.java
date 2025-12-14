package com.accounting.repository.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.dashboard.DashboardAuditLog;

@Repository
public interface DashboardAuditLogRepository extends JpaRepository<DashboardAuditLog, UUID> {

    Page<DashboardAuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<DashboardAuditLog> findByCompanyIdAndUserIdOrderByCreatedAtDesc(
            Long companyId, Long userId, Pageable pageable);

    Page<DashboardAuditLog> findByCompanyIdAndActionOrderByCreatedAtDesc(
            Long companyId, String action, Pageable pageable);

    @Query("SELECT a FROM DashboardAuditLog a WHERE a.companyId = :companyId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<DashboardAuditLog> findByCompanyIdSince(
            @Param("companyId") Long companyId,
            @Param("since") Instant since);

    @Query("SELECT COUNT(a) FROM DashboardAuditLog a WHERE a.companyId = :companyId AND a.action = :action AND a.userId = :userId AND a.createdAt >= :since")
    long countByUserAndActionSince(
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("since") Instant since);

    @Query("SELECT a.action, COUNT(a) FROM DashboardAuditLog a WHERE a.companyId = :companyId AND a.createdAt >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(
            @Param("companyId") Long companyId,
            @Param("since") Instant since);
}
