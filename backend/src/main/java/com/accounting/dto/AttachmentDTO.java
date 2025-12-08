package com.accounting.dto;

import java.time.Instant;
import java.util.UUID;

import com.accounting.entity.AttachmentEntityType;

/**
 * Unified DTO for attachment details across all entity types.
 * Replaces VoucherAttachmentDTO, PurchaseBillAttachmentDTO, and
 * SalesInvoiceAttachmentDTO.
 */
public class AttachmentDTO {

    private UUID id;
    private AttachmentEntityType entityType;
    private UUID entityId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Instant uploadedAt;
    private Long uploadedBy;
    private String uploadedByName;

    public AttachmentDTO() {
    }

    public AttachmentDTO(
            UUID id,
            AttachmentEntityType entityType,
            UUID entityId,
            String fileName,
            String mimeType,
            Long fileSize,
            Instant uploadedAt,
            Long uploadedBy,
            String uploadedByName) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
        this.uploadedByName = uploadedByName;
    }

    // Convenience getters for backward compatibility

    /**
     * Get entity ID as voucherId (for backward compatibility).
     */
    public UUID getVoucherId() {
        return entityType == AttachmentEntityType.VOUCHER ? entityId : null;
    }

    /**
     * Get entity ID as purchaseBillId (for backward compatibility).
     */
    public UUID getPurchaseBillId() {
        return entityType == AttachmentEntityType.PURCHASE_BILL ? entityId : null;
    }

    /**
     * Get entity ID as salesInvoiceId (for backward compatibility).
     */
    public UUID getSalesInvoiceId() {
        return entityType == AttachmentEntityType.SALES_INVOICE ? entityId : null;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AttachmentEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(AttachmentEntityType entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
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
}
