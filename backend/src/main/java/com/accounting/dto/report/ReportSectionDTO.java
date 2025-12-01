package com.accounting.dto.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a section in a financial report.
 * Examples: "ASSETS", "LIABILITIES", "EQUITY", "REVENUE", "EXPENSES"
 */
public class ReportSectionDTO {

  private String sectionCode; // Section identifier (e.g., "ASSETS", "LIABILITIES", "EQUITY")
  private String sectionName; // Section display name
  private BigDecimal total; // Section total amount
  private List<ReportLineDTO> lines; // Line items in this section

  public ReportSectionDTO() {
    this.total = BigDecimal.ZERO;
    this.lines = new ArrayList<>();
  }

  public ReportSectionDTO(
      String sectionCode, String sectionName, BigDecimal total, List<ReportLineDTO> lines) {
    this.sectionCode = sectionCode;
    this.sectionName = sectionName;
    this.total = total != null ? total : BigDecimal.ZERO;
    this.lines = lines != null ? lines : new ArrayList<>();
  }

  // Getters and setters

  public String getSectionCode() {
    return sectionCode;
  }

  public void setSectionCode(String sectionCode) {
    this.sectionCode = sectionCode;
  }

  public String getSectionName() {
    return sectionName;
  }

  public void setSectionName(String sectionName) {
    this.sectionName = sectionName;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal total) {
    this.total = total != null ? total : BigDecimal.ZERO;
  }

  public List<ReportLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<ReportLineDTO> lines) {
    this.lines = lines != null ? lines : new ArrayList<>();
  }

  /**
   * Add a line item to this section.
   *
   * @param line the line item to add
   */
  public void addLine(ReportLineDTO line) {
    if (this.lines == null) {
      this.lines = new ArrayList<>();
    }
    this.lines.add(line);
  }

  /**
   * Calculate and update the section total from all line items.
   * Uses current period amounts.
   */
  public void calculateTotal() {
    if (this.lines == null || this.lines.isEmpty()) {
      this.total = BigDecimal.ZERO;
      return;
    }
    this.total =
        this.lines.stream()
            .map(ReportLineDTO::getCurrentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
