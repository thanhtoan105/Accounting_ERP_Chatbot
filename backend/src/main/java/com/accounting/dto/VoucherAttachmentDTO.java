package com.accounting.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for VoucherAttachment details.
 */
public class VoucherAttachmentDTO {

  private UUID id;
  private UUID voucherId;
  private String fileName;
  private String mimeType;
  private Long fileSize;
  private Instant uploadedAt;
  private Long uploadedBy;
  private String uploadedByName;

  public VoucherAttachmentDTO() {}

  public VoucherAttachmentDTO(
      UUID id,
      UUID voucherId,
      String fileName,
      String mimeType,
      Long fileSize,
      Instant uploadedAt,
      Long uploadedBy,
      String uploadedByName) {
    this.id = id;
    this.voucherId = voucherId;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
    this.uploadedAt = uploadedAt;
    this.uploadedBy = uploadedBy;
    this.uploadedByName = uploadedByName;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(UUID voucherId) {
    this.voucherId = voucherId;
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
