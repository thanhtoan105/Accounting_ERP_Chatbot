package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.entity.PurchaseBillStatus;

/**
 * Lightweight DTO for PurchaseBill list view. Excludes full details for performance.
 */
public class PurchaseBillListDTO {

  private UUID id;
  private String billNumber;
  private LocalDate billDate;
  private LocalDate dueDate;
  private String supplierName;
  private String supplierCode;
  private String reference;
  private PurchaseBillStatus status;
  private BigDecimal totalAmount;
  private BigDecimal vatAmount;
  private String createdByName;
  private String approvedByName;
  private Integer attachmentCount;
  private UUID postedVoucherId;

  public PurchaseBillListDTO() {}

  public PurchaseBillListDTO(
      UUID id,
      String billNumber,
      LocalDate billDate,
      LocalDate dueDate,
      String supplierName,
      String supplierCode,
      String reference,
      PurchaseBillStatus status,
      BigDecimal totalAmount,
      BigDecimal vatAmount,
      String createdByName,
      String approvedByName,
      Integer attachmentCount,
      UUID postedVoucherId) {
    this.id = id;
    this.billNumber = billNumber;
    this.billDate = billDate;
    this.dueDate = dueDate;
    this.supplierName = supplierName;
    this.supplierCode = supplierCode;
    this.reference = reference;
    this.status = status;
    this.totalAmount = totalAmount;
    this.vatAmount = vatAmount;
    this.createdByName = createdByName;
    this.approvedByName = approvedByName;
    this.attachmentCount = attachmentCount;
    this.postedVoucherId = postedVoucherId;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
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

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public String getApprovedByName() {
    return approvedByName;
  }

  public void setApprovedByName(String approvedByName) {
    this.approvedByName = approvedByName;
  }

  public Integer getAttachmentCount() {
    return attachmentCount;
  }

  public void setAttachmentCount(Integer attachmentCount) {
    this.attachmentCount = attachmentCount;
  }

  public UUID getPostedVoucherId() {
    return postedVoucherId;
  }

  public void setPostedVoucherId(UUID postedVoucherId) {
    this.postedVoucherId = postedVoucherId;
  }
}
