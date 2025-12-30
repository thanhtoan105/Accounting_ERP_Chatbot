package com.accounting.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for a voucher line item.
 */
public class VoucherLineDTO {

  private Integer lineNumber; // Optional - auto-assigned if not provided

  @NotNull(message = "Account ID is required")
  private Long accountId;

  @NotNull(message = "Debit amount is required")
  private BigDecimal debit;

  @NotNull(message = "Credit amount is required")
  private BigDecimal credit;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  // Optional dimension fields
  private Long customerId;

  private Long supplierId;

  private Long itemId;

  public VoucherLineDTO() {}

  public VoucherLineDTO(
      Integer lineNumber,
      Long accountId,
      BigDecimal debit,
      BigDecimal credit,
      String description,
      Long customerId,
      Long supplierId,
      Long itemId) {
    this.lineNumber = lineNumber;
    this.accountId = accountId;
    this.debit = debit;
    this.credit = credit;
    this.description = description;
    this.customerId = customerId;
    this.supplierId = supplierId;
    this.itemId = itemId;
  }

  // Getters and setters
  public Integer getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(Integer lineNumber) {
    this.lineNumber = lineNumber;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public BigDecimal getDebit() {
    return debit;
  }

  public void setDebit(BigDecimal debit) {
    this.debit = debit;
  }

  public BigDecimal getCredit() {
    return credit;
  }

  public void setCredit(BigDecimal credit) {
    this.credit = credit;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public Long getItemId() {
    return itemId;
  }

  public void setItemId(Long itemId) {
    this.itemId = itemId;
  }
}
