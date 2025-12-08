package com.accounting.entity;

import java.time.Instant;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "ap_audit_backups")
@Data
public class APAuditBackup implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "backup_date", nullable = false)
    private Instant backupDate;

    @Column(name = "archive_path", nullable = false)
    private String archivePath;

    @Column(name = "hash", nullable = false)
    private String hash; // SHA-256 of the archive

    @Column(name = "record_count")
    private Integer recordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "created_by")
    private Long createdBy;

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }
}
