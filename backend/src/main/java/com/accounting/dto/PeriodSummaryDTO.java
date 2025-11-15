package com.accounting.dto;

import com.accounting.entity.PeriodStatus;
import java.time.LocalDate;

/**
 * DTO for period summary information used in dashboard badges.
 */
public class PeriodSummaryDTO {

  private String periodName;
  private PeriodStatus status;
  private String statusDisplay;
  private LocalDate startDate;
  private LocalDate endDate;
  private Long draftVouchersCount;
  private Long postedVouchersCount;
  private Boolean hasDraftVouchers;
  private String postingFlowStatus;
  private Boolean isCurrentPeriod;

  public PeriodSummaryDTO() {}

  public PeriodSummaryDTO(String periodName, PeriodStatus status, String statusDisplay,
                         LocalDate startDate, LocalDate endDate, Long draftVouchersCount,
                         Long postedVouchersCount, Boolean hasDraftVouchers,
                         String postingFlowStatus, Boolean isCurrentPeriod) {
    this.periodName = periodName;
    this.status = status;
    this.statusDisplay = statusDisplay;
    this.startDate = startDate;
    this.endDate = endDate;
    this.draftVouchersCount = draftVouchersCount;
    this.postedVouchersCount = postedVouchersCount;
    this.hasDraftVouchers = hasDraftVouchers;
    this.postingFlowStatus = postingFlowStatus;
    this.isCurrentPeriod = isCurrentPeriod;
  }

  // Getters and Setters
  public String getPeriodName() {
    return periodName;
  }

  public void setPeriodName(String periodName) {
    this.periodName = periodName;
  }

  public PeriodStatus getStatus() {
    return status;
  }

  public void setStatus(PeriodStatus status) {
    this.status = status;
  }

  public String getStatusDisplay() {
    return statusDisplay;
  }

  public void setStatusDisplay(String statusDisplay) {
    this.statusDisplay = statusDisplay;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public Long getDraftVouchersCount() {
    return draftVouchersCount;
  }

  public void setDraftVouchersCount(Long draftVouchersCount) {
    this.draftVouchersCount = draftVouchersCount;
  }

  public Long getPostedVouchersCount() {
    return postedVouchersCount;
  }

  public void setPostedVouchersCount(Long postedVouchersCount) {
    this.postedVouchersCount = postedVouchersCount;
  }

  public Boolean getHasDraftVouchers() {
    return hasDraftVouchers;
  }

  public void setHasDraftVouchers(Boolean hasDraftVouchers) {
    this.hasDraftVouchers = hasDraftVouchers;
  }

  public String getPostingFlowStatus() {
    return postingFlowStatus;
  }

  public void setPostingFlowStatus(String postingFlowStatus) {
    this.postingFlowStatus = postingFlowStatus;
  }

  public Boolean getIsCurrentPeriod() {
    return isCurrentPeriod;
  }

  public void setIsCurrentPeriod(Boolean isCurrentPeriod) {
    this.isCurrentPeriod = isCurrentPeriod;
  }

  @Override
  public String toString() {
    return "PeriodSummaryDTO{" +
        "periodName='" + periodName + '\'' +
        ", status=" + status +
        ", draftVouchersCount=" + draftVouchersCount +
        ", postedVouchersCount=" + postedVouchersCount +
        ", hasDraftVouchers=" + hasDraftVouchers +
        ", postingFlowStatus='" + postingFlowStatus + '\'' +
        ", isCurrentPeriod=" + isCurrentPeriod +
        '}';
  }
}