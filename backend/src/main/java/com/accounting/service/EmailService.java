package com.accounting.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

public interface EmailService {
  void sendPasswordResetEmail(String toEmail, String resetToken);

  /**
   * Send invitation email to user.
   *
   * @param toEmail invitee email address
   * @param invitationToken invitation token for registration link
   * @param inviterName name of user who sent the invitation
   * @param companyName company name
   * @param role role assigned to user
   * @param expiresAt invitation expiration date/time
   * @param request HTTP request (for logging purposes)
   */
  void sendInvitationEmail(
      String toEmail,
      String invitationToken,
      String inviterName,
      String companyName,
      String role,
      java.time.Instant expiresAt,
      HttpServletRequest request);
}

