package com.accounting.dto;

import com.accounting.validation.ValidRole;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating a user. All fields are optional.
 */
public class UpdateUserRequest {

  @Size(max = 255, message = "Full name must not exceed 255 characters")
  private String fullName;

  @ValidRole private String role;

  private String status; // ACTIVE, INACTIVE, LOCKED

  public UpdateUserRequest() {}

  public UpdateUserRequest(String fullName, String role, String status) {
    this.fullName = fullName;
    this.role = role;
    this.status = status;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
