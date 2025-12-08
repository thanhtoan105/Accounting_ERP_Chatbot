package com.accounting.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_audit_entries")
public class ImportAuditEntry {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "import_type", nullable = false, length = 64)
    private String importType;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_payload", columnDefinition = "JSONB")
    private String beforePayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_payload", columnDefinition = "JSONB")
    private String afterPayload;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getImportType() {
        return importType;
    }

    public void setImportType(String importType) {
        this.importType = importType;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBeforePayload() {
        return beforePayload;
    }

    public void setBeforePayload(String beforePayload) {
        this.beforePayload = beforePayload;
    }

    public String getAfterPayload() {
        return afterPayload;
    }

    public void setAfterPayload(String afterPayload) {
        this.afterPayload = afterPayload;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
