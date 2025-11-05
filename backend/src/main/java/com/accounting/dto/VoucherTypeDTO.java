package com.accounting.dto;

/**
 * DTO for Voucher Type list and detail views.
 * Includes account information for display.
 */
public class VoucherTypeDTO {

  private Long id;
  private Long companyId;
  private String typeCode;
  private String typeName;
  private Long debitAccountId;
  private String debitAccountCode;
  private String debitAccountName;
  private Long creditAccountId;
  private String creditAccountCode;
  private String creditAccountName;
  private String description;
  private String status;
  private String createdAt;
  private String updatedAt;

  public VoucherTypeDTO() {}

  public VoucherTypeDTO(
      Long id,
      Long companyId,
      String typeCode,
      String typeName,
      Long debitAccountId,
      String debitAccountCode,
      String debitAccountName,
      Long creditAccountId,
      String creditAccountCode,
      String creditAccountName,
      String description,
      String status,
      String createdAt,
      String updatedAt) {
    this.id = id;
    this.companyId = companyId;
    this.typeCode = typeCode;
    this.typeName = typeName;
    this.debitAccountId = debitAccountId;
    this.debitAccountCode = debitAccountCode;
    this.debitAccountName = debitAccountName;
    this.creditAccountId = creditAccountId;
    this.creditAccountCode = creditAccountCode;
    this.creditAccountName = creditAccountName;
    this.description = description;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  // Getters and setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getTypeCode() {
    return typeCode;
  }

  public void setTypeCode(String typeCode) {
    this.typeCode = typeCode;
  }

  public String getTypeName() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  public Long getDebitAccountId() {
    return debitAccountId;
  }

  public void setDebitAccountId(Long debitAccountId) {
    this.debitAccountId = debitAccountId;
  }

  public String getDebitAccountCode() {
    return debitAccountCode;
  }

  public void setDebitAccountCode(String debitAccountCode) {
    this.debitAccountCode = debitAccountCode;
  }

  public String getDebitAccountName() {
    return debitAccountName;
  }

  public void setDebitAccountName(String debitAccountName) {
    this.debitAccountName = debitAccountName;
  }

  public Long getCreditAccountId() {
    return creditAccountId;
  }

  public void setCreditAccountId(Long creditAccountId) {
    this.creditAccountId = creditAccountId;
  }

  public String getCreditAccountCode() {
    return creditAccountCode;
  }

  public void setCreditAccountCode(String creditAccountCode) {
    this.creditAccountCode = creditAccountCode;
  }

  public String getCreditAccountName() {
    return creditAccountName;
  }

  public void setCreditAccountName(String creditAccountName) {
    this.creditAccountName = creditAccountName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}




