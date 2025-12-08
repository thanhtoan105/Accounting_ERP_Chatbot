package com.accounting.dto;

import com.accounting.validation.ValidRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for creating an invitation.
 */
public class CreateInvitationRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Email must be valid")
  private String email;

  @ValidRole private String role; // Optional, defaults to 'accountant'

  public CreateInvitationRequest() {}

  public CreateInvitationRequest(String email, String role) {
    this.email = email;
    this.role = role;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}
