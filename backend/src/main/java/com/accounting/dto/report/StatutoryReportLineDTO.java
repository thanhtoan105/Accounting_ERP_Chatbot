package com.accounting.dto.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a single line item in a statutory financial report.
 * Enhanced for TT200 compliance with comparison period variance and drill-down support.
 */
public class StatutoryReportLineDTO {

  private String lineCode;            // TT200 line code (e.g., '100', '110')
  private String lineName;            // Vietnamese line name
  private String lineNameEnglish;     // English translation
  private Integer level;              // Indentation level (1=main, 2=sub, 3=detail)
  private boolean isCalculated;       // TRUE for lines computed from other lines
  private String formula;             // Formula for calculated lines
  private String accountPattern;      // Account pattern for hover tooltip

  // Current period values
  private BigDecimal currentAmount;   // Amount for current period

  // Comparison period values (optional)
  private BigDecimal priorAmount;     // Amount for prior/comparison period
  private BigDecimal variance;        // currentAmount - priorAmount
  private BigDecimal variancePercent; // (variance / priorAmount) * 100

  // Drill-down support
  private boolean hasDrillDown;       // TRUE if line can be drilled into
  private List<String> contributingAccounts; // Account codes contributing to this line

  public StatutoryReportLineDTO() {
    this.currentAmount = BigDecimal.ZERO;
    this.priorAmount = BigDecimal.ZERO;
    this.variance = BigDecimal.ZERO;
    this.level = 1;
    this.isCalculated = false;
    this.hasDrillDown = false;
    this.contributingAccounts = new ArrayList<>();
  }

  /**
   * Builder pattern constructor for convenience.
   */
  public StatutoryReportLineDTO(String lineCode, String lineName, String lineNameEnglish,
      Integer level, BigDecimal currentAmount) {
    this();
    this.lineCode = lineCode;
    this.lineName = lineName;
    this.lineNameEnglish = lineNameEnglish;
    this.level = level != null ? level : 1;
    this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
  }

  // Getters and Setters

  public String getLineCode() {
    return lineCode;
  }

  public void setLineCode(String lineCode) {
    this.lineCode = lineCode;
  }

  public String getLineName() {
    return lineName;
  }

  public void setLineName(String lineName) {
    this.lineName = lineName;
  }

  public String getLineNameEnglish() {
    return lineNameEnglish;
  }

  public void setLineNameEnglish(String lineNameEnglish) {
    this.lineNameEnglish = lineNameEnglish;
  }

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer level) {
    this.level = level != null ? level : 1;
  }

  public boolean isCalculated() {
    return isCalculated;
  }

  public void setCalculated(boolean calculated) {
    isCalculated = calculated;
  }

  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  public String getAccountPattern() {
    return accountPattern;
  }

  public void setAccountPattern(String accountPattern) {
    this.accountPattern = accountPattern;
  }

  public BigDecimal getCurrentAmount() {
    return currentAmount;
  }

  public void setCurrentAmount(BigDecimal currentAmount) {
    this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
  }

  public BigDecimal getPriorAmount() {
    return priorAmount;
  }

  public void setPriorAmount(BigDecimal priorAmount) {
    this.priorAmount = priorAmount != null ? priorAmount : BigDecimal.ZERO;
  }

  public BigDecimal getVariance() {
    return variance;
  }

  public void setVariance(BigDecimal variance) {
    this.variance = variance;
  }

  public BigDecimal getVariancePercent() {
    return variancePercent;
  }

  public void setVariancePercent(BigDecimal variancePercent) {
    this.variancePercent = variancePercent;
  }

  public boolean isHasDrillDown() {
    return hasDrillDown;
  }

  public void setHasDrillDown(boolean hasDrillDown) {
    this.hasDrillDown = hasDrillDown;
  }

  public List<String> getContributingAccounts() {
    return contributingAccounts;
  }

  public void setContributingAccounts(List<String> contributingAccounts) {
    this.contributingAccounts = contributingAccounts != null ? contributingAccounts : new ArrayList<>();
  }

  /**
   * Calculate variance fields based on current and prior amounts.
   * Should be called after both amounts are set.
   */
  public void calculateVariance() {
    if (currentAmount != null && priorAmount != null) {
      this.variance = currentAmount.subtract(priorAmount);

      // Calculate variance percentage (avoid division by zero)
      if (priorAmount.compareTo(BigDecimal.ZERO) != 0) {
        this.variancePercent = variance
            .multiply(new BigDecimal("100"))
            .divide(priorAmount.abs(), 2, RoundingMode.HALF_UP);
      } else if (currentAmount.compareTo(BigDecimal.ZERO) != 0) {
        // If prior is 0 but current is not, show 100% or -100%
        this.variancePercent = currentAmount.compareTo(BigDecimal.ZERO) > 0
            ? new BigDecimal("100")
            : new BigDecimal("-100");
      } else {
        this.variancePercent = BigDecimal.ZERO;
      }
    }
  }

  /**
   * Check if this line has non-zero value.
   *
   * @return true if current amount is non-zero
   */
  public boolean hasValue() {
    return currentAmount != null && currentAmount.compareTo(BigDecimal.ZERO) != 0;
  }

  /**
   * Check if this line has any value (current or prior).
   *
   * @return true if either current or prior amount is non-zero
   */
  public boolean hasAnyValue() {
    boolean hasCurrentValue = currentAmount != null && currentAmount.compareTo(BigDecimal.ZERO) != 0;
    boolean hasPriorValue = priorAmount != null && priorAmount.compareTo(BigDecimal.ZERO) != 0;
    return hasCurrentValue || hasPriorValue;
  }

  /**
   * Add a contributing account code.
   *
   * @param accountCode the account code to add
   */
  public void addContributingAccount(String accountCode) {
    if (this.contributingAccounts == null) {
      this.contributingAccounts = new ArrayList<>();
    }
    if (!this.contributingAccounts.contains(accountCode)) {
      this.contributingAccounts.add(accountCode);
    }
  }
}
