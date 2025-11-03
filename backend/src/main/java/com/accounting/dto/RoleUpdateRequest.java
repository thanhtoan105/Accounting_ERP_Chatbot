package com.accounting.dto;

import com.accounting.validation.ValidRole;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for updating a user's role.
 */
public class RoleUpdateRequest {

  @NotBlank(message = "Role is required")
  @ValidRole
  private String role;

  public RoleUpdateRequest() {}

  public RoleUpdateRequest(String role) {
    this.role = role;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}

