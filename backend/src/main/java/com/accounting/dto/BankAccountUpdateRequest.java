package com.accounting.dto;

import com.accounting.entity.BankAccount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request DTO for updating an existing BankAccount.
 * All fields are optional - only provided fields will be updated.
 */
public class BankAccountUpdateRequest {

  @Size(max = 255)
  private String bankName;

  @Size(max = 255)
  private String branch;

  private BankAccount.AccountType type;

  @DecimalMin(value = "0.0", message = "Opening balance must be non-negative")
  private BigDecimal openingBalance;

  private String reason; // Optional reason for change (for audit)

  public BankAccountUpdateRequest() {}

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public BankAccount.AccountType getType() {
    return type;
  }

  public void setType(BankAccount.AccountType type) {
    this.type = type;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

