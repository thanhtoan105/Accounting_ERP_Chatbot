package com.accounting.service.impl.ap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.APAuditBackup;
import com.accounting.entity.AuditLog;
import com.accounting.repository.APAuditBackupRepository;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.APAuditBackupService;
import com.accounting.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional
public class APAuditBackupServiceImpl implements APAuditBackupService {

    private static final Logger logger = LoggerFactory.getLogger(APAuditBackupServiceImpl.class);
    private static final String BACKUP_DIR = "backups";

    private final APAuditBackupRepository backupRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public APAuditBackupServiceImpl(
            APAuditBackupRepository backupRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService) {
        this.backupRepository = backupRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
        } catch (IOException e) {
            logger.error("Failed to create backup directory", e);
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public APAuditBackup createBackup(Long companyId) {
        logger.info("Starting AP audit backup for company {}", companyId);
        
        APAuditBackup backup = new APAuditBackup();
        backup.setCompanyId(companyId);
        backup.setBackupDate(Instant.now());
        backup.setStatus(APAuditBackup.Status.PENDING);
        backup.setCreatedBy(getCurrentUserId());
        backup.setHash("PENDING");
        backup.setArchivePath("PENDING");
        backup = backupRepository.save(backup);

        try {
            // Fetch all logs for company
            Specification<AuditLog> spec = (root, query, cb) -> 
                cb.equal(root.get("companyId"), companyId);
            List<AuditLog> logs = auditLogRepository.findAll(spec);
            
            // Serialize logs to JSON
            String jsonContent = objectMapper.writeValueAsString(logs);
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            
            // Create ZIP archive
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry("audit_logs_" + companyId + "_" + Instant.now().toEpochMilli() + ".json");
                zos.putNextEntry(entry);
                zos.write(jsonBytes);
                zos.closeEntry();
            }
            byte[] zipBytes = baos.toByteArray();
            
            // Calculate hash
            String hash = calculateHash(zipBytes);
            
            // Save to file system
            String fileName = "backup_" + companyId + "_" + UUID.randomUUID() + ".zip";
            Path filePath = Paths.get(BACKUP_DIR, fileName);
            Files.write(filePath, zipBytes);
            
            // Update backup entity
            backup.setStatus(APAuditBackup.Status.COMPLETED);
            backup.setRecordCount(logs.size());
            backup.setHash(hash);
            backup.setArchivePath(filePath.toAbsolutePath().toString());
            backup = backupRepository.save(backup);
            
            // Audit the backup creation
            // Currently no specific method in AuditService for backup creation, using generic if needed
            // But we can add one later. For now logger is sufficient as per existing patterns?
            // AC#4 says "Log backup creation via AuditService".
            // I'll use a generic event logging if available or assume logAuditExport is close enough?
            // Let's use logAuditExport with format "BACKUP_ARCHIVE"
            auditService.logAuditExport(companyId, getCurrentUserId(), logs.size(), "BACKUP_ARCHIVE", hash, null);
            
            logger.info("Backup completed for company {}: {} records, hash {}", companyId, logs.size(), hash);
            return backup;
            
        } catch (Exception e) {
            logger.error("Backup failed for company {}", companyId, e);
            backup.setStatus(APAuditBackup.Status.FAILED);
            backupRepository.save(backup);
            throw new RuntimeException("Backup failed", e);
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public Page<APAuditBackup> listBackups(String status, String startDate, String endDate, Pageable pageable) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        Specification<APAuditBackup> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), APAuditBackup.Status.valueOf(status.toUpperCase())));
            }
            
            if (startDate != null) {
                LocalDate start = LocalDate.parse(startDate);
                predicates.add(cb.greaterThanOrEqualTo(root.get("backupDate"), start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            
            if (endDate != null) {
                LocalDate end = LocalDate.parse(endDate);
                predicates.add(cb.lessThanOrEqualTo(root.get("backupDate"), end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return backupRepository.findAll(spec, pageable);
    }

    @Override
    @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'CFO', 'ADMIN')")
    public byte[] downloadBackup(Long backupId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        APAuditBackup backup = backupRepository.findById(backupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup not found"));

        if (!backup.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        try {
            byte[] content = Files.readAllBytes(Paths.get(backup.getArchivePath()));
            
            // Log download
            // Using generic audit export log for now
            auditService.logAuditExport(companyId, getCurrentUserId(), backup.getRecordCount(), "BACKUP_DOWNLOAD", backup.getHash(), null);
            
            return content;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read backup file", e);
        }
    }

    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
