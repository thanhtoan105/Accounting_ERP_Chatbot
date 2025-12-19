package com.accounting.repository.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.analytics.ExportApprovalRequest;
import com.accounting.entity.analytics.ExportApprovalStatus;

@Repository
public interface ExportApprovalRequestRepository extends JpaRepository<ExportApprovalRequest, Long> {

    List<ExportApprovalRequest> findByCompanyIdAndStatus(Long companyId, ExportApprovalStatus status);

    @Query("SELECT e FROM ExportApprovalRequest e WHERE e.companyId = :companyId AND e.status = 'PENDING' ORDER BY e.requestedAt DESC")
    List<ExportApprovalRequest> findPendingByCompanyId(@Param("companyId") Long companyId);

    Optional<ExportApprovalRequest> findByDownloadToken(String downloadToken);

    Optional<ExportApprovalRequest> findByIdAndCompanyId(Long id, Long companyId);

    List<ExportApprovalRequest> findByRequesterIdAndCompanyIdOrderByRequestedAtDesc(Long requesterId, Long companyId);

    @Query("SELECT e FROM ExportApprovalRequest e WHERE e.status = 'APPROVED' AND e.downloadExpiry < :now")
    List<ExportApprovalRequest> findExpiredApprovals(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE ExportApprovalRequest e SET e.status = 'EXPIRED', e.updatedAt = :now WHERE e.status = 'APPROVED' AND e.downloadExpiry < :now")
    int markExpiredApprovals(@Param("now") Instant now);

    long countByCompanyIdAndRequesterIdAndStatus(Long companyId, Long requesterId, ExportApprovalStatus status);
}
