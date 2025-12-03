package com.accounting.dto.audit;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Filter criteria for querying Cash & Bank audit logs.
 * Supports pagination, date range, and multi-field filtering.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Date range is required and cannot exceed 12 months</li>
 *   <li>Page size must be 10, 20, or 50</li>
 *   <li>Export is limited to 10,000 records</li>
 * </ul>
 */
public class CashAuditQueryDTO {

  @NotNull(message = "dateFrom is required")
  private LocalDate dateFrom;

  @NotNull(message = "dateTo is required")
  private LocalDate dateTo;

  private Long bankAccountId;

  private List<String> actionTypes;

  private Long userId;

  private String entityType;

  @Min(value = 0, message = "page must be >= 0")
  private int page = 0;

  @Min(value = 10, message = "size must be at least 10")
  @Max(value = 50, message = "size cannot exceed 50")
  private int size = 20;

  public CashAuditQueryDTO() {
  }

  public CashAuditQueryDTO(
      LocalDate dateFrom,
      LocalDate dateTo,
      Long bankAccountId,
      List<String> actionTypes,
      Long userId,
      String entityType,
      int page,
      int size) {
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
    this.bankAccountId = bankAccountId;
    this.actionTypes = actionTypes;
    this.userId = userId;
    this.entityType = entityType;
    this.page = page;
    this.size = size;
  }

  /**
   * Validates that the date range does not exceed 12 months.
   *
   * @return true if date range is valid (≤ 12 months)
   */
  @AssertTrue(message = "Date range cannot exceed 12 months")
  public boolean isDateRangeValid() {
    if (dateFrom == null || dateTo == null) {
      return true; // Let @NotNull handle null validation
    }
    if (dateFrom.isAfter(dateTo)) {
      return false;
    }
    long monthsBetween = ChronoUnit.MONTHS.between(dateFrom, dateTo);
    return monthsBetween <= 12;
  }

  /**
   * Validates that size is one of the allowed values: 10, 20, or 50.
   *
   * @return true if size is valid
   */
  @AssertTrue(message = "size must be 10, 20, or 50")
  public boolean isSizeValid() {
    return size == 10 || size == 20 || size == 50;
  }

  // Getters and Setters

  public LocalDate getDateFrom() {
    return dateFrom;
  }

  public void setDateFrom(LocalDate dateFrom) {
    this.dateFrom = dateFrom;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
  }

  public List<String> getActionTypes() {
    return actionTypes;
  }

  public void setActionTypes(List<String> actionTypes) {
    this.actionTypes = actionTypes;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  /**
   * Builder for creating CashAuditQueryDTO instances.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long bankAccountId;
    private List<String> actionTypes;
    private Long userId;
    private String entityType;
    private int page = 0;
    private int size = 20;

    public Builder dateFrom(LocalDate dateFrom) {
      this.dateFrom = dateFrom;
      return this;
    }

    public Builder dateTo(LocalDate dateTo) {
      this.dateTo = dateTo;
      return this;
    }

    public Builder bankAccountId(Long bankAccountId) {
      this.bankAccountId = bankAccountId;
      return this;
    }

    public Builder actionTypes(List<String> actionTypes) {
      this.actionTypes = actionTypes;
      return this;
    }

    public Builder userId(Long userId) {
      this.userId = userId;
      return this;
    }

    public Builder entityType(String entityType) {
      this.entityType = entityType;
      return this;
    }

    public Builder page(int page) {
      this.page = page;
      return this;
    }

    public Builder size(int size) {
      this.size = size;
      return this;
    }

    public CashAuditQueryDTO build() {
      return new CashAuditQueryDTO(
          dateFrom, dateTo, bankAccountId, actionTypes, userId, entityType, page, size);
    }
  }
}
