package com.accounting.dto;

/**
 * DTO representing overdue count for dashboard badge.
 */
public class OverdueCountDTO {

  private long count;

  public OverdueCountDTO() {}

  public OverdueCountDTO(long count) {
    this.count = count;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }
}
