package com.accounting.dto.cashbook;

import java.time.Instant;

/**
 * DTO for cash book export job status.
 * Used for async export tracking (AC6.4-05).
 */
public class CashBookExportJobDTO {

    private String jobId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private Long bankAccountId;
    private String format;
    private Long recordCount;
    private Integer progress; // 0-100 percentage
    private Instant createdAt;
    private Instant completedAt;
    private String downloadUrl;
    private String errorMessage;

    public CashBookExportJobDTO() {
    }

    public CashBookExportJobDTO(String jobId, Long bankAccountId, String format, Long recordCount) {
        this.jobId = jobId;
        this.bankAccountId = bankAccountId;
        this.format = format;
        this.recordCount = recordCount;
        this.status = "PENDING";
        this.progress = 0;
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
