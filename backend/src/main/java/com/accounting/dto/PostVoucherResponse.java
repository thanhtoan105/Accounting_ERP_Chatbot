package com.accounting.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for posting a voucher.
 * Contains the posted voucher, generated journal entries, and validation errors (if any).
 */
public class PostVoucherResponse {

  private VoucherDTO voucher;
  private List<JournalEntryDTO> journalEntries;
  private Map<String, Object> validationErrors; // ValidationErrorMap format

  public PostVoucherResponse() {}

  public PostVoucherResponse(VoucherDTO voucher, List<JournalEntryDTO> journalEntries) {
    this.voucher = voucher;
    this.journalEntries = journalEntries;
  }

  public PostVoucherResponse(
      VoucherDTO voucher,
      List<JournalEntryDTO> journalEntries,
      Map<String, Object> validationErrors) {
    this.voucher = voucher;
    this.journalEntries = journalEntries;
    this.validationErrors = validationErrors;
  }

  public VoucherDTO getVoucher() {
    return voucher;
  }

  public void setVoucher(VoucherDTO voucher) {
    this.voucher = voucher;
  }

  public List<JournalEntryDTO> getJournalEntries() {
    return journalEntries;
  }

  public void setJournalEntries(List<JournalEntryDTO> journalEntries) {
    this.journalEntries = journalEntries;
  }

  public Map<String, Object> getValidationErrors() {
    return validationErrors;
  }

  public void setValidationErrors(Map<String, Object> validationErrors) {
    this.validationErrors = validationErrors;
  }
}
