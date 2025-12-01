package com.accounting.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for sales invoice file attachments.
 */
public class SalesInvoiceAttachmentDTO {

    private UUID id;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Instant uploadedAt;
    private Long uploadedBy;
    private String uploadedByName;
    private String downloadUrl; // Generated signed URL

    public SalesInvoiceAttachmentDTO() {
    }

    public SalesInvoiceAttachmentDTO(
            UUID id,
            String fileName,
            String mimeType,
            Long fileSize,
            Instant uploadedAt,
            Long uploadedBy,
            String uploadedByName,
            String downloadUrl) {
        this.id = id;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
        this.uploadedByName = uploadedByName;
        this.downloadUrl = downloadUrl;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public void setUploadedByName(String uploadedByName) {
        this.uploadedByName = uploadedByName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
