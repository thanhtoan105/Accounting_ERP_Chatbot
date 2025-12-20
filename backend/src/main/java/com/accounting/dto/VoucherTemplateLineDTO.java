package com.accounting.dto;

public class VoucherTemplateLineDTO {

  private Long id;
  private Integer lineNumber;
  private Long debitAccountId;
  private String debitAccountCode;
  private String debitAccountName;
  private Long creditAccountId;
  private String creditAccountCode;
  private String creditAccountName;
  private String defaultDescription;
  private boolean requiresCustomer;
  private boolean requiresSupplier;
  private boolean requiresCostCenter;
  private boolean lockAccounts;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Integer getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(Integer lineNumber) {
    this.lineNumber = lineNumber;
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

  public String getDefaultDescription() {
    return defaultDescription;
  }

  public void setDefaultDescription(String defaultDescription) {
    this.defaultDescription = defaultDescription;
  }

  public boolean isRequiresCustomer() {
    return requiresCustomer;
  }

  public void setRequiresCustomer(boolean requiresCustomer) {
    this.requiresCustomer = requiresCustomer;
  }

  public boolean isRequiresSupplier() {
    return requiresSupplier;
  }

  public void setRequiresSupplier(boolean requiresSupplier) {
    this.requiresSupplier = requiresSupplier;
  }

  public boolean isRequiresCostCenter() {
    return requiresCostCenter;
  }

  public void setRequiresCostCenter(boolean requiresCostCenter) {
    this.requiresCostCenter = requiresCostCenter;
  }

  public boolean isLockAccounts() {
    return lockAccounts;
  }

  public void setLockAccounts(boolean lockAccounts) {
    this.lockAccounts = lockAccounts;
  }
}
