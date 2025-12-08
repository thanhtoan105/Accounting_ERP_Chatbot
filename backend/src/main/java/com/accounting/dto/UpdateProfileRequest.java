package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating user profile (self-edit).
 */
public class UpdateProfileRequest {

  @NotBlank(message = "Full name is required")
  @Size(max = 255, message = "Full name must not exceed 255 characters")
  private String fullName;

  public UpdateProfileRequest() {}

  public UpdateProfileRequest(String fullName) {
    this.fullName = fullName;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }
}
