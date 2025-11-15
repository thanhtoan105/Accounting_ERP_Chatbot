package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * VoucherAttachment entity representing file attachments for vouchers.
 * Attachments are stored in Supabase Storage with metadata in the database.
 */
@Entity
@Table(name = "voucher_attachments")
public class VoucherAttachment implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @ManyToOne
  @JoinColumn(name = "voucher_id", nullable = false)
  private Voucher voucher;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @NotBlank
  @Column(name = "storage_path", nullable = false, length = 500)
  private String storagePath;

  @NotBlank
  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  @NotNull
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @NotNull
  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  @NotNull
  @Column(name = "uploaded_by", nullable = false)
  private Long uploadedBy;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "uploaded_by", insertable = false, updatable = false)
  private User uploadedByUser;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Voucher getVoucher() {
    return voucher;
  }

  public void setVoucher(Voucher voucher) {
    this.voucher = voucher;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
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

  public User getUploadedByUser() {
    return uploadedByUser;
  }

  public void setUploadedByUser(User uploadedByUser) {
    this.uploadedByUser = uploadedByUser;
  }
}

