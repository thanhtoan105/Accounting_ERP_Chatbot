package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for reminder result.
 */
public class ReminderResultDTO {

  private boolean success;
  private String message;
  private List<String> sentTo = new ArrayList<>();
  private List<String> failedTo = new ArrayList<>();

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
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

