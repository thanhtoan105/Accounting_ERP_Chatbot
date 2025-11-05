package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing Voucher Type.
 */
public class VoucherTypeUpdateRequest {

  @NotBlank(message = "Type code is required")
  @Size(max = 50, message = "Type code must not exceed 50 characters")
  private String typeCode;

  @NotBlank(message = "Type name is required")
  @Size(max = 255, message = "Type name must not exceed 255 characters")
  private String typeName;

  private Long debitAccountId;

  private Long creditAccountId;

  @Size(max = 1000, message = "Description must not exceed 1000 characters")
  private String description;

  public VoucherTypeUpdateRequest() {}

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

  public Long getCreditAccountId() {
    return creditAccountId;
  }

  public void setCreditAccountId(Long creditAccountId) {
    this.creditAccountId = creditAccountId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}


