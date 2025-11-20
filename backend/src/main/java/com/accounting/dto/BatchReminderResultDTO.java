package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for batch reminder result.
 */
public class BatchReminderResultDTO {

  private int totalSuppliers;
  private int successCount;
  private int failedCount;
  private List<String> sentTo = new ArrayList<>();
  private List<String> failedTo = new ArrayList<>();

  public int getTotalSuppliers() {
    return totalSuppliers;
  }

  public void setTotalSuppliers(int totalSuppliers) {
    this.totalSuppliers = totalSuppliers;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public void setSuccessCount(int successCount) {
    this.successCount = successCount;
  }

  public int getFailedCount() {
    return failedCount;
  }

  public void setFailedCount(int failedCount) {
    this.failedCount = failedCount;
  }

  public List<String> getSentTo() {
    return sentTo;
  }

  public void setSentTo(List<String> sentTo) {
    this.sentTo = sentTo;
  }

  public List<String> getFailedTo() {
    return failedTo;
  }

  public void setFailedTo(List<String> failedTo) {
    this.failedTo = failedTo;
  }
}

