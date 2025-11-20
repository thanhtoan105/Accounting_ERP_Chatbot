package com.accounting.dto;

import com.accounting.entity.PurchaseBillStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for full PurchaseBill details. Used for single purchase bill retrieval.
 */
public class PurchaseBillDTO {

  private UUID id;
  private Long companyId;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private String billNumber;
  private LocalDate billDate;
  private LocalDate dueDate;
  private String reference;
  private String description;
  private PurchaseBillStatus status;
  private BigDecimal totalAmount;
  private BigDecimal vatAmount;
  private Long createdById;
  private String createdByName;
  private Long approvedById;
  private String approvedByName;
  private UUID postedVoucherId;
  private Instant createdAt;
  private Instant updatedAt;
  private Integer attachmentCount;
  private List<PurchaseBillLineDTO> lines; // Purchase bill line items

  public PurchaseBillDTO() {}

  public PurchaseBillDTO(
      UUID id,
      Long companyId,
      Long supplierId,
      String supplierName,
      String supplierCode,
      String billNumber,
      LocalDate billDate,
      LocalDate dueDate,
      String reference,
      String description,
      PurchaseBillStatus status,
      BigDecimal totalAmount,
      BigDecimal vatAmount,
      Long createdById,
      String createdByName,
      Long approvedById,
      String approvedByName,
      UUID postedVoucherId,
      Instant createdAt,
      Instant updatedAt,
      Integer attachmentCount) {
    this.id = id;
    this.companyId = companyId;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.supplierCode = supplierCode;
    this.billNumber = billNumber;
    this.billDate = billDate;
    this.dueDate = dueDate;
    this.reference = reference;
    this.description = description;
    this.status = status;
    this.totalAmount = totalAmount;
    this.vatAmount = vatAmount;
    this.createdById = createdById;
    this.createdByName = createdByName;
    this.approvedById = approvedById;
    this.approvedByName = approvedByName;
    this.postedVoucherId = postedVoucherId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.attachmentCount = attachmentCount;
  }

  // Getters and setters
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

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public String getSupplierCode() {
    return supplierCode;
  }

  public void setSupplierCode(String supplierCode) {
    this.supplierCode = supplierCode;
  }

  public String getBillNumber() {
    return billNumber;
  }

  public void setBillNumber(String billNumber) {
    this.billNumber = billNumber;
  }

  public LocalDate getBillDate() {
    return billDate;
  }

  public void setBillDate(LocalDate billDate) {
    this.billDate = billDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public PurchaseBillStatus getStatus() {
    return status;
  }

  public void setStatus(PurchaseBillStatus status) {
    this.status = status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public void setVatAmount(BigDecimal vatAmount) {
    this.vatAmount = vatAmount;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public Long getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(Long approvedById) {
    this.approvedById = approvedById;
  }

  public String getApprovedByName() {
    return approvedByName;
  }

  public void setApprovedByName(String approvedByName) {
    this.approvedByName = approvedByName;
  }

  public UUID getPostedVoucherId() {
    return postedVoucherId;
  }

  public void setPostedVoucherId(UUID postedVoucherId) {
    this.postedVoucherId = postedVoucherId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Integer getAttachmentCount() {
    return attachmentCount;
  }

  public void setAttachmentCount(Integer attachmentCount) {
    this.attachmentCount = attachmentCount;
  }

  public List<PurchaseBillLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<PurchaseBillLineDTO> lines) {
    this.lines = lines;
  }
}

