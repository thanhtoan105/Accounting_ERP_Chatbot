package com.accounting.entity;

import java.time.Instant;

import com.accounting.enums.Role;
import com.accounting.repository.CompanyScopedEntity;
import com.accounting.validation.ValidRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "role", nullable = false, length = 50)
  @ValidRole
  private String role;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "failed_login_count", nullable = false)
  private Integer failedLoginCount = 0;

  @Column(name = "reset_token", length = 255)
  private String resetToken;

  @Column(name = "reset_token_expiry")
  private Instant resetTokenExpiry;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE";

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
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

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
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

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public Integer getFailedLoginCount() {
    return failedLoginCount;
  }

  public void setFailedLoginCount(Integer failedLoginCount) {
    this.failedLoginCount = failedLoginCount;
  }

  public String getResetToken() {
    return resetToken;
  }

  public void setResetToken(String resetToken) {
    this.resetToken = resetToken;
  }

  public Instant getResetTokenExpiry() {
    return resetTokenExpiry;
  }

  public void setResetTokenExpiry(Instant resetTokenExpiry) {
    this.resetTokenExpiry = resetTokenExpiry;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Get role as Role enum. Returns null if role is invalid.
   *
   * @return Role enum or null
   */
  public Role getRoleEnum() {
    return Role.fromString(this.role);
  }

  /**
   * Set role from Role enum.
   *
   * @param role Role enum value
   */
  public void setRoleEnum(Role role) {
    if (role != null) {
      this.role = role.getValue();
    }
  }

  /**
   * Check if user has a specific role.
   *
   * @param role Role to check
   * @return true if user has the role, false otherwise
   */
  public boolean hasRole(Role role) {
    if (role == null || this.role == null) {
      return false;
    }
    return this.role.equals(role.getValue());
  }
}
