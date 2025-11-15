package com.accounting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class VoucherTemplateSummaryDTO {

  private UUID id;
  private String name;
  private String description;

  @JsonProperty("isActive")
  private boolean active;

  private Instant createdAt;
  private String createdBy;
  private AccountPreview firstLineDebitAccount;
  private AccountPreview firstLineCreditAccount;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @JsonProperty("isActive")
  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public AccountPreview getFirstLineDebitAccount() {
    return firstLineDebitAccount;
  }

  public void setFirstLineDebitAccount(AccountPreview firstLineDebitAccount) {
    this.firstLineDebitAccount = firstLineDebitAccount;
  }

  public AccountPreview getFirstLineCreditAccount() {
    return firstLineCreditAccount;
  }

  public void setFirstLineCreditAccount(AccountPreview firstLineCreditAccount) {
    this.firstLineCreditAccount = firstLineCreditAccount;
  }

  public static class AccountPreview {
    private String code;
    private String name;

    public AccountPreview() {}

    public AccountPreview(String code, String name) {
      this.code = code;
      this.name = name;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}

