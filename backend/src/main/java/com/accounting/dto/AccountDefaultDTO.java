package com.accounting.dto;

/**
 * DTO for individual account default entry.
 */
public class AccountDefaultDTO {

  private String columnName;
  private Long accountId;
  private String accountCode;
  private String accountName;

  public AccountDefaultDTO() {}

  public AccountDefaultDTO(String columnName, Long accountId, String accountCode,
      String accountName) {
    this.columnName = columnName;
    this.accountId = accountId;
    this.accountCode = accountCode;
    this.accountName = accountName;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public void setAccountCode(String accountCode) {
    this.accountCode = accountCode;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

