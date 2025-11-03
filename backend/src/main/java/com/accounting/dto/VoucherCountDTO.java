package com.accounting.dto;

/**
 * DTO for voucher counts by status. Used for badge display.
 */
public class VoucherCountDTO {

  private long draft;
  private long posted;
  private long unposted;

  public VoucherCountDTO() {}

  public VoucherCountDTO(long draft, long posted, long unposted) {
    this.draft = draft;
    this.posted = posted;
    this.unposted = unposted;
  }

  public long getDraft() {
    return draft;
  }

  public void setDraft(long draft) {
    this.draft = draft;
  }

  public long getPosted() {
    return posted;
  }

  public void setPosted(long posted) {
    this.posted = posted;
  }

  public long getUnposted() {
    return unposted;
  }

  public void setUnposted(long unposted) {
    this.unposted = unposted;
  }
}
