package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for batch reminder request.
 */
public class BatchReminderRequestDTO {

  private List<Long> supplierIds = new ArrayList<>();
  private List<String> recipients = new ArrayList<>();
  private String message;

  public List<Long> getSupplierIds() {
    return supplierIds;
  }

  public void setSupplierIds(List<Long> supplierIds) {
    this.supplierIds = supplierIds;
  }

  public List<String> getRecipients() {
    return recipients;
  }

  public void setRecipients(List<String> recipients) {
    this.recipients = recipients;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

