package com.accounting.service;

import com.accounting.entity.Invitation;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Service for invitation management operations.
 */
public interface InvitationService {

  /**
   * Create a new invitation. Generates secure token, sets expiration (7 days), and sends email.
   *
   * @param email invitee email address
   * @param role role to assign (defaults to 'accountant' if null)
   * @param createdByUserId ID of user creating the invitation
   * @param httpRequest HTTP request for audit logging
   * @return created invitation
   */
  Invitation createInvitation(
      String email, String role, Long createdByUserId, HttpServletRequest httpRequest);

  /**
   * Validate invitation token. Checks if token exists, is pending, and not expired.
   *
   * @param token invitation token
   * @return invitation if valid, null otherwise
   */
  Invitation validateInvitation(String token);

  /**
   * Accept invitation. Creates user account with company and role, marks invitation as accepted.
   *
   * @param token invitation token
   * @param password user password
   * @param fullName user full name
   * @param httpRequest HTTP request for audit logging
   * @return created user
   */
  com.accounting.entity.User acceptInvitation(
      String token, String password, String fullName, HttpServletRequest httpRequest);

  /**
   * Cancel invitation. Marks invitation as CANCELLED.
   *
   * @param invitationId invitation ID
   * @param httpRequest HTTP request for audit logging
   */
  void cancelInvitation(Long invitationId, HttpServletRequest httpRequest);

  /**
   * List all invitations for the current company.
   *
   * @return list of invitations
   */
  java.util.List<Invitation> listInvitations();
}

