package com.accounting.service;

import java.util.List;
import java.util.UUID;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.PeriodSummaryDTO;

public interface ComparisonPresetService {
  /**
   * Get suggested periods for comparison based on mode.
   *
   * @param currentPeriodId The base period to compare against
   * @param mode YOY (Year-over-Year), MOM (Month-over-Month), or QUARTERLY
   * @return Up to 3 suggested comparison periods
   */
  List<AccountingPeriodDTO> getSuggestedPeriods(UUID currentPeriodId, String mode);

  /**
   * Get suggested comparison periods based on mode.
   *
   * @param basePeriodId The base/current period to compare from
   * @param mode Comparison mode: "YOY", "MOM", "QUARTERLY", "CUSTOM"
   * @param maxPeriods Maximum periods to return (typically 4)
   * @return List of suggested period IDs (may be fewer if periods don't exist)
   */
  List<UUID> getSuggestedPeriodIds(UUID basePeriodId, String mode, int maxPeriods);

  /**
   * Get available periods for comparison for the current company.
   *
   * @return List of all periods available for comparison
   */
  List<PeriodSummaryDTO> getAvailablePeriods();
}
