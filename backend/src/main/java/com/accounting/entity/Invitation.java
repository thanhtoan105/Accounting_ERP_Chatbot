package com.accounting.entity;

import java.time.Instant;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Invitation entity for user invitation system.
 * Implements CompanyScopedEntity for multi-tenancy.
 */
@Entity
@Table(name = "invitations")
public class Invitation implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "token", nullable = false, unique = true, length = 64)
  private String token;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "created_by")
  private Long createdBy; // User ID of inviter

  @Column(name = "role", nullable = false, length = 50)
  private String role;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "status", nullable = false, length = 20)
  private String status; // PENDING, ACCEPTED, EXPIRED, CANCELLED

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "invited_by")
  private Long invitedBy;

  @Column(name = "token_hash", length = 64)
  private String tokenHash;

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

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  /**
   * Check if invitation is expired.
   *
   * @return true if expired, false otherwise
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /**
   * Check if invitation is pending.
   *
   * @return true if status is PENDING, false otherwise
   */
  public boolean isPending() {
    return "PENDING".equals(status);
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(Instant acceptedAt) {
    this.acceptedAt = acceptedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public Long getInvitedBy() {
    return invitedBy;
  }

  public void setInvitedBy(Long invitedBy) {
    this.invitedBy = invitedBy;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isAccepted() {
    return acceptedAt != null;
  }

  public boolean isActive() {
    return isPending() && !isExpired() && !isRevoked() && !isAccepted();
  }
}
