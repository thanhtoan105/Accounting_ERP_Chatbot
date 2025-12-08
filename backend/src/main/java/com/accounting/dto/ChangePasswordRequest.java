package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for changing password.
 */
public class ChangePasswordRequest {

  @NotBlank(message = "Current password is required")
  private String currentPassword;

  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "New password must be at least 8 characters")
  private String newPassword;

  @NotBlank(message = "Confirm password is required")
  private String confirmPassword;

  public ChangePasswordRequest() {}

  public ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
    this.currentPassword = currentPassword;
    this.newPassword = newPassword;
    this.confirmPassword = confirmPassword;
  }

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }

  public String getConfirmPassword() {
    return confirmPassword;
  }

  public void setConfirmPassword(String confirmPassword) {
    this.confirmPassword = confirmPassword;
  }
}
