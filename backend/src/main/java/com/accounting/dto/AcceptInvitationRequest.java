package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for accepting an invitation.
 */
public class AcceptInvitationRequest {

  @NotBlank(message = "Password is required")
  @Size(min = 8, message = "Password must be at least 8 characters")
  private String password;

  @NotBlank(message = "Password confirmation is required")
  private String confirmPassword;

  @NotBlank(message = "Full name is required")
  private String fullName;

  public AcceptInvitationRequest() {}

  public AcceptInvitationRequest(String password, String confirmPassword, String fullName) {
    this.password = password;
    this.confirmPassword = confirmPassword;
    this.fullName = fullName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getConfirmPassword() {
    return confirmPassword;
  }

  public void setConfirmPassword(String confirmPassword) {
    this.confirmPassword = confirmPassword;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }
}

