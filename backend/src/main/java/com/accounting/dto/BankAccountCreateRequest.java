package com.accounting.dto;

import java.math.BigDecimal;

import com.accounting.entity.BankAccount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new BankAccount.
 */
public class BankAccountCreateRequest {

  @NotBlank(message = "Account number is required")
  @Size(max = 50)
  private String accountNumber;

  @NotBlank(message = "Bank name is required")
  @Size(max = 255)
  private String bankName;

  @Size(max = 255)
  private String branch;

  @NotNull(message = "Account type is required")
  private BankAccount.AccountType type;

  @NotNull(message = "Opening balance is required")
  @DecimalMin(value = "0.0", message = "Opening balance must be non-negative")
  private BigDecimal openingBalance;

  private Boolean active = true;

  @Size(max = 20, message = "GL account code must be at most 20 characters")
  private String glAccountCode;

  public BankAccountCreateRequest() {
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

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

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getGlAccountCode() {
    return glAccountCode;
  }

  public void setGlAccountCode(String glAccountCode) {
    this.glAccountCode = glAccountCode;
  }
}
