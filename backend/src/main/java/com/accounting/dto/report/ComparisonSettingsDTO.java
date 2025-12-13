package com.accounting.dto.report;

import java.math.BigDecimal;

public record ComparisonSettingsDTO(
    double varianceThresholdPercent,
    BigDecimal varianceThresholdAbsolute,
    String defaultComparisonMode,
    boolean showSparklines,
    boolean hideImmaterialDefault) {

  public static ComparisonSettingsDTO defaults() {
    return new ComparisonSettingsDTO(10.0, new BigDecimal("1000000"), "YOY", true, false);
  }
}
