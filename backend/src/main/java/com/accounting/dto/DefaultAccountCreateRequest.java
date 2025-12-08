package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new Default Account.
 */
public class DefaultAccountCreateRequest {

  @NotBlank(message = "Voucher type is required")
  @Size(max = 50, message = "Voucher type must not exceed 50 characters")
  private String voucherType;

  @NotBlank(message = "Entry name is required")
  @Size(max = 255, message = "Entry name must not exceed 255 characters")
  private String entryName;

  @NotEmpty(message = "At least one account default is required")
  @Valid
  private List<AccountDefaultRequest> accountDefaults = new ArrayList<>();

  public DefaultAccountCreateRequest() {}

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

    @NotBlank(message = "Column name is required")
    @Size(max = 255, message = "Column name must not exceed 255 characters")
    private String columnName;

    @jakarta.validation.constraints.NotNull(message = "Account ID is required")
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
