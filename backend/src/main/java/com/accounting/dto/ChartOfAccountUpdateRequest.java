package com.accounting.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing Chart of Account.
 * All fields are optional except id (passed as path variable).
 */
public class ChartOfAccountUpdateRequest {

  @Size(min = 1, max = 20)
  @Pattern(regexp = "^\\d{1,4}$", message = "Account code must be numeric (1-4 digits)")
  private String code;

  @Size(max = 255)
  private String name;

  @Size(max = 255)
  private String nameEnglish;

  @Size(max = 1000)
  private String description;

  @Size(max = 50)
  private String type; // Asset, Liability, Equity, Revenue, Expense

  @Size(max = 50)
  private String normalSide; // Debit, Credit, Hermaphrodite

  private Long parentId; // Optional, null for root accounts

  private Integer orderingPosition;

  public ChartOfAccountUpdateRequest() {}

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

  public String getNameEnglish() {
    return nameEnglish;
  }

  public void setNameEnglish(String nameEnglish) {
    this.nameEnglish = nameEnglish;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getNormalSide() {
    return normalSide;
  }

  public void setNormalSide(String normalSide) {
    this.normalSide = normalSide;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public Integer getOrderingPosition() {
    return orderingPosition;
  }

  public void setOrderingPosition(Integer orderingPosition) {
    this.orderingPosition = orderingPosition;
  }
}

