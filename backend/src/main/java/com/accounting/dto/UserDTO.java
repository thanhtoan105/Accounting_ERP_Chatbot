package com.accounting.dto;

/**
 * DTO for User responses. Excludes sensitive fields like password_hash.
 */
public class UserDTO {

  private Long id;
  private String email;
  private String fullName;
  private String role;
  private String status;
  private Long companyId;
  private java.time.Instant createdAt;
  private java.time.Instant updatedAt;

  public UserDTO() {}

  public UserDTO(
      Long id,
      String email,
      String fullName,
      String role,
      String status,
      Long companyId,
      java.time.Instant createdAt,
      java.time.Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.fullName = fullName;
    this.role = role;
    this.status = status;
    this.companyId = companyId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
