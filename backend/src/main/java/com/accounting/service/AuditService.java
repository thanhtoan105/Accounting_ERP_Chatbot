package com.accounting.service;

import com.accounting.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuditService {
  void logLoginSuccess(User user, HttpServletRequest request);

  void logLoginFailure(User user, String email, String reason, HttpServletRequest request);

  void logPasswordResetRequest(User user, HttpServletRequest request);

  void logPasswordResetCompleted(User user, HttpServletRequest request);

  /**
   * Log role change operation. Records old_role, new_role, changed_by_user_id, target_user_id,
   * timestamp, and IP address.
   *
   * @param targetUser user whose role is being changed
   * @param oldRole previous role value
   * @param newRole new role value
   * @param changedByUserId ID of user who made the change
   * @param request HTTP request for IP address and user agent
   */
  void logRoleChange(
      User targetUser, String oldRole, String newRole, Long changedByUserId, HttpServletRequest request);

  /**
   * Log invitation creation. Records inviter, invitee email, company, status, timestamp, and IP
   * address.
   *
   * @param inviterUserId ID of user who created the invitation
   * @param inviteeEmail email address of invited user
   * @param companyId company ID for the invitation
   * @param role role assigned to the invitation
   * @param request HTTP request for IP address and user agent
   */
  void logInvitationCreated(
      Long inviterUserId,
      String inviteeEmail,
      Long companyId,
      String role,
      HttpServletRequest request);

  /**
   * Log invitation acceptance. Records inviter, invitee email, company, status, timestamp, and IP
   * address.
   *
   * @param invitationId ID of the invitation
   * @param inviterUserId ID of user who created the invitation
   * @param inviteeEmail email address of invited user
   * @param companyId company ID for the invitation
   * @param request HTTP request for IP address and user agent
   */
  void logInvitationAccepted(
      Long invitationId,
      Long inviterUserId,
      String inviteeEmail,
      Long companyId,
      HttpServletRequest request);

  /**
   * Log invitation expiration. Records inviter, invitee email, company, status, timestamp, and IP
   * address.
   *
   * @param invitationId ID of the invitation
   * @param inviterUserId ID of user who created the invitation
   * @param inviteeEmail email address of invited user
   * @param companyId company ID for the invitation
   * @param request HTTP request for IP address and user agent
   */
  void logInvitationExpired(
      Long invitationId,
      Long inviterUserId,
      String inviteeEmail,
      Long companyId,
      HttpServletRequest request);

  /**
   * Log invitation cancellation. Records inviter, invitee email, company, status, timestamp, and IP
   * address.
   *
   * @param invitationId ID of the invitation
   * @param inviterUserId ID of user who created/cancelled the invitation
   * @param inviteeEmail email address of invited user
   * @param companyId company ID for the invitation
   * @param request HTTP request for IP address and user agent
   */
  void logInvitationCancelled(
      Long invitationId,
      Long inviterUserId,
      String inviteeEmail,
      Long companyId,
      HttpServletRequest request);

  /**
   * Log user creation. Records user details, creator, timestamp, and IP address.
   *
   * @param user created user
   * @param createdByUserId ID of user who created this user
   * @param request HTTP request for IP address and user agent
   */
  void logUserCreated(User user, Long createdByUserId, HttpServletRequest request);

  /**
   * Log user update. Records user details, updater, old/new values, timestamp, and IP address.
   *
   * @param user updated user
   * @param updatedByUserId ID of user who made the update
   * @param oldValues map of old field values (key: field name, value: old value)
   * @param newValues map of new field values (key: field name, value: new value)
   * @param request HTTP request for IP address and user agent
   */
  void logUserUpdated(
      User user,
      Long updatedByUserId,
      java.util.Map<String, String> oldValues,
      java.util.Map<String, String> newValues,
      HttpServletRequest request);

  /**
   * Log user deactivation. Records user details, deactivator, timestamp, and IP address.
   *
   * @param user deactivated user
   * @param deactivatedByUserId ID of user who deactivated this user
   * @param request HTTP request for IP address and user agent
   */
  void logUserDeactivated(User user, Long deactivatedByUserId, HttpServletRequest request);

  /**
   * Log user activation. Records user details, activator, timestamp, and IP address.
   *
   * @param user activated user
   * @param activatedByUserId ID of user who activated this user
   * @param request HTTP request for IP address and user agent
   */
  void logUserActivated(User user, Long activatedByUserId, HttpServletRequest request);

  /**
   * Log password reset initiated by admin. Records admin user, target user, timestamp, and IP address.
   *
   * @param targetUser user whose password is being reset
   * @param adminUserId ID of admin who initiated the reset
   * @param request HTTP request for IP address and user agent
   */
  void logPasswordResetByAdmin(User targetUser, Long adminUserId, HttpServletRequest request);

  /**
   * Log profile update. Records user details, updated fields, timestamp, and IP address.
   *
   * @param user user whose profile was updated
   * @param updatedFields map of updated fields (key: field name, value: new value)
   * @param request HTTP request for IP address and user agent
   */
  void logProfileUpdated(User user, java.util.Map<String, String> updatedFields, HttpServletRequest request);

  /**
   * Log password change. Records user details, timestamp, and IP address.
   *
   * @param user user who changed their password
   * @param request HTTP request for IP address and user agent
   */
  void logPasswordChanged(User user, HttpServletRequest request);

  /**
   * Log voucher deletion. Records voucher ID, deletion reason, user who deleted, timestamp, and IP address.
   *
   * @param voucherId voucher ID that was deleted
   * @param voucherNumber voucher number for reference
   * @param reason deletion reason (required)
   * @param deletedByUserId ID of user who deleted the voucher
   * @param request HTTP request for IP address and user agent
   */
  void logVoucherDeleted(
      java.util.UUID voucherId,
      String voucherNumber,
      String reason,
      Long deletedByUserId,
      HttpServletRequest request);

  /**
   * Log company settings update with old/new values.
   *
   * @param companyId target company id
   * @param updatedByUserId user who performed the update
   * @param oldValues map of old values
   * @param newValues map of new values
   * @param request HTTP request for IP address and user agent
   */
  void logCompanySettingsUpdated(
      Long companyId,
      Long updatedByUserId,
      java.util.Map<String, String> oldValues,
      java.util.Map<String, String> newValues,
      HttpServletRequest request);
}

