package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for reminder request.
 */
public class ReminderRequestDTO {

  private Long supplierId;
  private List<UUID> billIds = new ArrayList<>();
  private List<String> recipients = new ArrayList<>();
  private String message;

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public List<UUID> getBillIds() {
    return billIds;
  }

  public void setBillIds(List<UUID> billIds) {
    this.billIds = billIds;
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
