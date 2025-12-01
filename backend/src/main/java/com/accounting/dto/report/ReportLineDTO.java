package com.accounting.dto.report;

import java.math.BigDecimal;

/**
 * DTO representing a single line item in a financial report section.
 * Follows TT200 format for statutory compliance (Vietnam accounting standards).
 */
public class ReportLineDTO {

  private String code; // TT200 code (e.g., "100", "110", "111")
  private String name; // Line item name
  private String note; // Reference note number (optional)
  private BigDecimal currentAmount; // Amount for current period
  private BigDecimal previousAmount; // Amount for previous period (for comparison)
  private Integer level; // Indentation level (1 = main, 2 = sub, 3 = detail)

  public ReportLineDTO() {
    this.currentAmount = BigDecimal.ZERO;
    this.previousAmount = BigDecimal.ZERO;
    this.level = 1;
  }

  public ReportLineDTO(
      String code,
      String name,
      String note,
      BigDecimal currentAmount,
      BigDecimal previousAmount,
      Integer level) {
    this.code = code;
    this.name = name;
    this.note = note;
    this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
    this.previousAmount = previousAmount != null ? previousAmount : BigDecimal.ZERO;
    this.level = level != null ? level : 1;
  }

  // Getters and setters

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

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public BigDecimal getCurrentAmount() {
    return currentAmount;
  }

  public void setCurrentAmount(BigDecimal currentAmount) {
    this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
  }

  public BigDecimal getPreviousAmount() {
    return previousAmount;
  }

  public void setPreviousAmount(BigDecimal previousAmount) {
    this.previousAmount = previousAmount != null ? previousAmount : BigDecimal.ZERO;
  }

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer level) {
    this.level = level != null ? level : 1;
  }
}
