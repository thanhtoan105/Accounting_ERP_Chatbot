package com.accounting.dto;

import java.time.Instant;

/**
 * DTO for invitation details (used for validation endpoint).
 */
public class InvitationResponse {

  private String email;
  private String companyName;
  private String role;
  private Instant expiresAt;

  public InvitationResponse() {}

  public InvitationResponse(String email, String companyName, String role, Instant expiresAt) {
    this.email = email;
    this.companyName = companyName;
    this.role = role;
    this.expiresAt = expiresAt;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}

