package com.accounting.service.analytics.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.analytics.ExportApprovalDTO;
import com.accounting.entity.User;
import com.accounting.entity.analytics.ExportApprovalRequest;
import com.accounting.entity.analytics.ExportApprovalStatus;
import com.accounting.repository.UserRepository;
import com.accounting.repository.analytics.ExportApprovalRequestRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.AnalyticsAuditService;
import com.accounting.service.analytics.AnalyticsAuditService.AuditAction;
import com.accounting.service.analytics.AnalyticsAuditService.ResourceType;
import com.accounting.service.analytics.AnalyticsExportService;
import com.accounting.service.analytics.ExportApprovalService;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class ExportApprovalServiceImpl implements ExportApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ExportApprovalServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ExportApprovalRequestRepository repository;
    private final UserRepository userRepository;
    private final AnalyticsExportService exportService;
    private final AnalyticsAuditService auditService;

    @Value("${analytics.export.large-export-threshold:10000}")
    private int largeExportThreshold;

    @Value("${analytics.export.download-expiry-hours:24}")
    private int downloadExpiryHours;

    public ExportApprovalServiceImpl(
            ExportApprovalRequestRepository repository,
            UserRepository userRepository,
            AnalyticsExportService exportService,
            AnalyticsAuditService auditService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.exportService = exportService;
        this.auditService = auditService;
    }

    @Override
    public ExportApprovalRequest requestLargeExport(String exportType, Integer rowCount, String queryParameters) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        ExportApprovalRequest request = new ExportApprovalRequest();
        request.setCompanyId(companyId);
        request.setRequesterId(userId);
        request.setExportType(exportType);
        request.setRowCount(rowCount);
        request.setQueryParameters(queryParameters);
        request.setStatus(ExportApprovalStatus.PENDING);
        request.setRequestedAt(Instant.now());

        ExportApprovalRequest saved = repository.save(request);

        log.info(
                "Large export request created: id={}, companyId={}, userId={}, exportType={}, rowCount={}",
                saved.getId(),
                companyId,
                userId,
                exportType,
                rowCount);

        auditService.logAction(
                companyId,
                userId,
                AuditAction.EXPORT_TRIGGERED,
                ResourceType.EXPORT,
                saved.getId().toString(),
                Map.of(
                        "action", "LARGE_EXPORT_REQUESTED",
                        "exportType", exportType,
                        "rowCount", rowCount,
                        "status", "PENDING"),
                null);

        return saved;
    }

    @Override
    public ExportApprovalDTO approve(Long requestId) {
        Long companyId = CompanyContext.getCompanyId();
        Long approverId = SecurityUtils.getCurrentUserId();

        ExportApprovalRequest request =
                repository.findByIdAndCompanyId(requestId, companyId).orElseThrow(() -> new EntityNotFoundException(
                        "Export approval request not found: " + requestId));

        if (request.getStatus() != ExportApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved. Current status: " + request.getStatus());
        }

        if (approverId.equals(request.getRequesterId())) {
            throw new IllegalStateException("Cannot approve your own export request. Another ADMIN or CFO must approve.");
        }

        request.setStatus(ExportApprovalStatus.APPROVED);
        request.setApproverId(approverId);
        request.setDecidedAt(Instant.now());
        request.setDownloadToken(generateDownloadToken());
        request.setDownloadExpiry(Instant.now().plus(downloadExpiryHours, ChronoUnit.HOURS));

        ExportApprovalRequest saved = repository.save(request);

        log.info(
                "Export request approved: id={}, companyId={}, approverId={}, expiresAt={}",
                requestId,
                companyId,
                approverId,
                saved.getDownloadExpiry());

        auditService.logAction(
                companyId,
                approverId,
                AuditAction.EXPORT_TRIGGERED,
                ResourceType.EXPORT,
                requestId.toString(),
                Map.of(
                        "action", "LARGE_EXPORT_APPROVED",
                        "requesterId", request.getRequesterId(),
                        "exportType", request.getExportType(),
                        "rowCount", request.getRowCount(),
                        "downloadExpiry", saved.getDownloadExpiry().toString()),
                null);

        return toDTO(saved);
    }

    @Override
    public ExportApprovalDTO reject(Long requestId, String reason) {
        Long companyId = CompanyContext.getCompanyId();
        Long approverId = SecurityUtils.getCurrentUserId();

        ExportApprovalRequest request =
                repository.findByIdAndCompanyId(requestId, companyId).orElseThrow(() -> new EntityNotFoundException(
                        "Export approval request not found: " + requestId));

        if (request.getStatus() != ExportApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected. Current status: " + request.getStatus());
        }

        if (approverId.equals(request.getRequesterId())) {
            throw new IllegalStateException("Cannot reject your own export request");
        }

        request.setStatus(ExportApprovalStatus.REJECTED);
        request.setApproverId(approverId);
        request.setDecidedAt(Instant.now());
        request.setRejectionReason(reason);

        ExportApprovalRequest saved = repository.save(request);

        log.info("Export request rejected: id={}, companyId={}, approverId={}, reason={}", requestId, companyId, approverId, reason);

        auditService.logAction(
                companyId,
                approverId,
                AuditAction.EXPORT_TRIGGERED,
                ResourceType.EXPORT,
                requestId.toString(),
                Map.of(
                        "action", "LARGE_EXPORT_REJECTED",
                        "requesterId", request.getRequesterId(),
                        "exportType", request.getExportType(),
                        "reason", reason),
                null);

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportApprovalDTO getById(Long requestId) {
        Long companyId = CompanyContext.getCompanyId();
        return repository
                .findByIdAndCompanyId(requestId, companyId)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Export approval request not found: " + requestId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportApprovalDTO> getPendingExports() {
        Long companyId = CompanyContext.getCompanyId();
        return repository.findPendingByCompanyId(companyId).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportApprovalDTO> getMyExportRequests() {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        return repository.findByRequesterIdAndCompanyIdOrderByRequestedAtDesc(userId, companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExportApprovalRequest getByDownloadToken(String token) {
        return repository.findByDownloadToken(token).orElse(null);
    }

    @Override
    public byte[] downloadExport(String token) {
        ExportApprovalRequest request = repository
                .findByDownloadToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid download token"));

        if (request.getStatus() != ExportApprovalStatus.APPROVED) {
            throw new IllegalStateException("Export is not approved");
        }

        if (request.isExpired()) {
            request.setStatus(ExportApprovalStatus.EXPIRED);
            repository.save(request);
            throw new IllegalStateException("Download link has expired");
        }

        byte[] data = exportService.exportDashboardToExcel(request.getCompanyId(), null, request.getExportType());

        request.incrementDownloadCount();
        repository.save(request);

        log.info(
                "Export downloaded: id={}, companyId={}, downloadCount={}",
                request.getId(),
                request.getCompanyId(),
                request.getDownloadCount());

        auditService.logAction(
                request.getCompanyId(),
                request.getRequesterId(),
                AuditAction.EXPORT_TRIGGERED,
                ResourceType.EXPORT,
                request.getId().toString(),
                Map.of(
                        "action", "LARGE_EXPORT_DOWNLOADED",
                        "downloadCount", request.getDownloadCount(),
                        "fileSize", data.length),
                null);

        return data;
    }

    @Override
    public void markAsDownloaded(Long requestId) {
        Long companyId = CompanyContext.getCompanyId();
        ExportApprovalRequest request =
                repository.findByIdAndCompanyId(requestId, companyId).orElseThrow(() -> new EntityNotFoundException(
                        "Export approval request not found: " + requestId));

        if (request.getStatus() == ExportApprovalStatus.APPROVED) {
            request.setStatus(ExportApprovalStatus.DOWNLOADED);
            repository.save(request);
        }
    }

    @Override
    @Scheduled(cron = "0 0 * * * *")
    public int cleanupExpired() {
        Instant now = Instant.now();
        int count = repository.markExpiredApprovals(now);
        if (count > 0) {
            log.info("Marked {} expired export approvals", count);
        }
        return count;
    }

    @Override
    public boolean requiresApproval(int rowCount) {
        return rowCount > largeExportThreshold;
    }

    @Override
    public int getLargeExportThreshold() {
        return largeExportThreshold;
    }

    private String generateDownloadToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ExportApprovalDTO toDTO(ExportApprovalRequest request) {
        String requesterName = null;
        String requesterEmail = null;
        String approverName = null;

        if (request.getRequesterId() != null) {
            User requester = userRepository.findById(request.getRequesterId()).orElse(null);
            if (requester != null) {
                requesterName = requester.getFullName();
                requesterEmail = requester.getEmail();
            }
        }

        if (request.getApproverId() != null) {
            User approver = userRepository.findById(request.getApproverId()).orElse(null);
            if (approver != null) {
                approverName = approver.getFullName();
            }
        }

        String downloadUrl = null;
        if (request.getDownloadToken() != null && request.canDownload()) {
            downloadUrl = "/api/v1/analytics/exports/download/" + request.getDownloadToken();
        }

        return new ExportApprovalDTO(
                request.getId(),
                request.getCompanyId(),
                request.getRequesterId(),
                requesterName,
                requesterEmail,
                request.getExportType(),
                request.getQueryParameters(),
                request.getRowCount(),
                request.getStatus(),
                request.getApproverId(),
                approverName,
                request.getRejectionReason(),
                request.getRequestedAt(),
                request.getDecidedAt(),
                downloadUrl,
                request.getDownloadExpiry(),
                request.getDownloadCount(),
                request.getCreatedAt());
    }
}
