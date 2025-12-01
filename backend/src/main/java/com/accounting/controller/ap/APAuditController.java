package com.accounting.controller.ap;

import com.accounting.dto.APAuditEventDTO;
import com.accounting.dto.APAuditTimelineDTO;
import com.accounting.dto.AbuseDetectionResultDTO;
import com.accounting.entity.APAuditBackup;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.APAuditBackupService;
import com.accounting.service.APAuditService;
import com.accounting.service.AuditService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ap-audit")
public class APAuditController {

    private final APAuditService apAuditService;
    private final APAuditBackupService backupService;
    private final AuditService auditService;

    public APAuditController(
            APAuditService apAuditService,
            APAuditBackupService backupService,
            AuditService auditService) {
        this.apAuditService = apAuditService;
        this.backupService = backupService;
        this.auditService = auditService;
    }

    @GetMapping("/timeline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<APAuditTimelineDTO>> getTimeline(
            @RequestParam Map<String, Object> filters,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apAuditService.getAuditTimeline(filters, pageable));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<APAuditEventDTO> getEventDetails(@PathVariable Long eventId) {
        return ResponseEntity.ok(apAuditService.getAuditEventDetails(eventId));
    }

    @GetMapping("/timeline/export")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<byte[]> exportTimeline(@RequestParam Map<String, Object> filters) {
        byte[] content = apAuditService.exportAuditTimeline(filters);
        String filename = "ap-audit-timeline-" + Instant.now().toEpochMilli() + ".pdf";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/abuse")
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<List<AbuseDetectionResultDTO>> getAbuseDetections(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "60") int timeWindowMinutes) {
        return ResponseEntity.ok(apAuditService.detectAbusePatterns(userId, timeWindowMinutes));
    }
    
    @GetMapping("/unauthorized-attempts")
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<Page<APAuditTimelineDTO>> getUnauthorizedAttempts(
            @RequestParam Map<String, Object> filters,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apAuditService.getUnauthorizedAttempts(filters, pageable));
    }

    @GetMapping("/backups")
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<Page<APAuditBackup>> listBackups(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(backupService.listBackups(status, startDate, endDate, pageable));
    }
    
    @PostMapping("/backups")
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<APAuditBackup> createBackup() {
        Long companyId = CompanyContext.getCompanyId();
        return ResponseEntity.ok(backupService.createBackup(companyId));
    }

    @GetMapping("/backups/{backupId}/download")
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public ResponseEntity<byte[]> downloadBackup(@PathVariable Long backupId) {
        byte[] content = backupService.downloadBackup(backupId);
        // Try to fetch backup to get filename/hash (or just use generic name)
        String filename = "backup-" + backupId + ".zip";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @PostMapping("/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> purgeAuditLogs(
            @RequestBody Map<String, Object> criteria) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = criteria.get("userId") != null ? Long.valueOf(criteria.get("userId").toString()) : null;
        String dateStr = (String) criteria.get("beforeDate");
        Instant beforeDate = dateStr != null ? LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        
        if (beforeDate == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "beforeDate is required"));
        }
        
        int count = auditService.purgeAuditLogs(companyId, userId, beforeDate, SecurityUtils.getCurrentUserId());
        
        return ResponseEntity.ok(Map.of(
            "purgedCount", count,
            "status", "COMPLETED"
        ));
    }
}
