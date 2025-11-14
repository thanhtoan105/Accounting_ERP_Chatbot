package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Default Account list and detail views.
 * Includes account default entries with populated account information.
 */
public class DefaultAccountDTO {

  private Long id;
  private Long companyId;
  private String voucherType;
  private String entryName;
  private String status;
  private String createdAt;
  private String updatedAt;
  private List<AccountDefaultDTO> accountDefaults = new ArrayList<>();

  public DefaultAccountDTO() {}

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

  public List<AccountDefaultDTO> getAccountDefaults() {
    return accountDefaults;
  }

  public void setAccountDefaults(List<AccountDefaultDTO> accountDefaults) {
    this.accountDefaults = accountDefaults;
  }
}

