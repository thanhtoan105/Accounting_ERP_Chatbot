package com.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for updating an existing Default Account.
 * All fields are optional for partial updates.
 */
public class DefaultAccountUpdateRequest {

  @Size(max = 50, message = "Voucher type must not exceed 50 characters")
  private String voucherType;

  @Size(max = 255, message = "Entry name must not exceed 255 characters")
  private String entryName;

  @Valid
  private List<AccountDefaultRequest> accountDefaults;

  public DefaultAccountUpdateRequest() {}

  public String getVoucherType() {
    return voucherType;
  }

  public void setVoucherType(String voucherType) {
    this.voucherType = voucherType;
  }

  public String getEntryName() {
    return entryName;
  }

  public void setEntryName(String entryName) {
    this.entryName = entryName;
  }

  public List<AccountDefaultRequest> getAccountDefaults() {
    return accountDefaults;
  }

  public void setAccountDefaults(List<AccountDefaultRequest> accountDefaults) {
    this.accountDefaults = accountDefaults;
  }

  /**
   * Nested DTO for account default entry in create/update requests.
   */
  public static class AccountDefaultRequest {

    @Size(max = 255, message = "Column name must not exceed 255 characters")
    private String columnName;

    private Long defaultAccountId;

    public AccountDefaultRequest() {}

    public String getColumnName() {
      return columnName;
    }

    public void setColumnName(String columnName) {
      this.columnName = columnName;
    }

    public Long getDefaultAccountId() {
      return defaultAccountId;
    }

    public void setDefaultAccountId(Long defaultAccountId) {
      this.defaultAccountId = defaultAccountId;
    }
  }
}

