package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for Chart of Account responses. Used in API endpoints.
 */
public class ChartOfAccountDTO {

  private Long id;
  private Long companyId;
  private String code;
  private String name;
  private String type;
  private String normalSide;
  private Boolean postable;
  private Long parentId;
  private String parentCode;
  private Integer orderingPosition;
  private BigDecimal balance; // Optional, for AC#11
  private Instant createdAt;
  private Instant updatedAt;

  public ChartOfAccountDTO() {}

  public ChartOfAccountDTO(
      Long id,
      Long companyId,
      String code,
      String name,
      String type,
      String normalSide,
      Boolean postable,
      Long parentId,
      String parentCode,
      Integer orderingPosition,
      BigDecimal balance,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.companyId = companyId;
    this.code = code;
    this.name = name;
    this.type = type;
    this.normalSide = normalSide;
    this.postable = postable;
    this.parentId = parentId;
    this.parentCode = parentCode;
    this.orderingPosition = orderingPosition;
    this.balance = balance;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

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

  public Boolean getPostable() {
    return postable;
  }

  public void setPostable(Boolean postable) {
    this.postable = postable;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getParentCode() {
    return parentCode;
  }

  public void setParentCode(String parentCode) {
    this.parentCode = parentCode;
  }

  public Integer getOrderingPosition() {
    return orderingPosition;
  }

  public void setOrderingPosition(Integer orderingPosition) {
    this.orderingPosition = orderingPosition;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
