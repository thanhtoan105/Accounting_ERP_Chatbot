package com.accounting.service.impl;

import com.accounting.service.EmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

  private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

  private final Resend resend;
  private final String fromEmail;
  private final String frontendUrl;

  public EmailServiceImpl(
      @Value("${resend.api-key:#{null}}") String apiKey,
      @Value("${resend.from-email:onboarding@resend.dev}") String fromEmail,
      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
    // Clean frontend URL - remove any comments or whitespace that might have been included
    String cleanedUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:5173";
    // Remove inline comments (everything after #)
    if (cleanedUrl.contains("#")) {
      cleanedUrl = cleanedUrl.substring(0, cleanedUrl.indexOf("#")).trim();
    }
    this.frontendUrl = cleanedUrl;

    // Validate and set fromEmail
    if (fromEmail == null || fromEmail.isBlank()) {
      logger.warn("Resend from-email not configured, using default: onboarding@resend.dev");
      this.fromEmail = "onboarding@resend.dev";
    } else {
      // Ensure format is valid: email@domain.com or Name <email@domain.com>
      String trimmed = fromEmail.trim();
      if (isValidEmailFormat(trimmed)) {
        this.fromEmail = trimmed;
      } else {
        logger.warn("Invalid from-email format: '{}', using default: onboarding@resend.dev", fromEmail);
        this.fromEmail = "onboarding@resend.dev";
      }
    }

    if (apiKey == null || apiKey.isBlank()) {
      logger.warn("Resend API key not configured. Email sending will be disabled.");
      this.resend = null;
    } else {
      this.resend = new Resend(apiKey);
      logger.info("Resend email service configured with from-email: {}", this.fromEmail);
    }
  }

  private boolean isValidEmailFormat(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    // Check format: email@domain.com or Name <email@domain.com>
    // Simple validation: contains @ and valid characters
    if (email.contains("<") && email.contains(">")) {
      // Format: Name <email@domain.com>
      int start = email.indexOf("<");
      int end = email.indexOf(">");
      if (start < end) {
        String emailPart = email.substring(start + 1, end).trim();
        return emailPart.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
      }
    } else {
      // Format: email@domain.com
      return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    return false;
  }

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetToken) {
    if (resend == null) {
      logger.warn("Email sending skipped - Resend API key not configured. Reset token for {}: {}", toEmail, resetToken);
      return;
    }

    String resetUrl = String.format("%s/reset-password?token=%s", frontendUrl, resetToken);

    String htmlContent = String.format(
        """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .button { display: inline-block; padding: 12px 24px; background-color: #007bff; color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                .button:hover { background-color: #0056b3; }
                .footer { margin-top: 30px; font-size: 12px; color: #666; }
              </style>
            </head>
            <body>
              <div class="container">
                <h2>Reset Your Password</h2>
                <p>You requested to reset your password. Click the button below to reset it:</p>
                <a href="%s" class="button">Reset Password</a>
                <p>Or copy and paste this link into your browser:</p>
                <p style="word-break: break-all; color: #007bff;">%s</p>
                <p>This link will expire in 30 minutes.</p>
                <p>If you didn't request a password reset, please ignore this email.</p>
                <div class="footer">
                  <p>This is an automated message. Please do not reply to this email.</p>
                </div>
              </div>
            </body>
            </html>
            """,
        resetUrl, resetUrl);

    try {
      logger.debug("Sending password reset email to {} from {}", toEmail, fromEmail);
      CreateEmailOptions emailOptions = CreateEmailOptions.builder()
          .from(fromEmail)
          .to(toEmail)
          .subject("Reset Your Password")
          .html(htmlContent)
          .build();

      CreateEmailResponse response = resend.emails().send(emailOptions);
      logger.info("Password reset email sent successfully to {} with ID: {}", toEmail, response.getId());
    } catch (ResendException e) {
      logger.error("Failed to send password reset email to {} from {}: {}", toEmail, fromEmail, e.getMessage(), e);
      // Log the error details for debugging
      if (e.getMessage() != null && e.getMessage().contains("from")) {
        logger.error("Invalid 'from' field format. Current value: '{}'. Expected format: 'email@example.com' or 'Name <email@example.com>'", fromEmail);
      }
      throw new RuntimeException("Failed to send password reset email", e);
    }
  }

  @Override
  public void sendInvitationEmail(
      String toEmail,
      String invitationToken,
      String inviterName,
      String companyName,
      String role,
      Instant expiresAt,
      HttpServletRequest request) {
    if (resend == null) {
      logger.warn(
          "Email sending skipped - Resend API key not configured. Invitation token for {}: {}",
          toEmail,
          invitationToken);
      return;
    }

    String invitationUrl =
        String.format("%s/invite/%s", frontendUrl, invitationToken);

    // Format expiration date
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    String expirationDate = formatter.format(expiresAt);

    String htmlContent =
        String.format(
            """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #007bff; color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .button:hover { background-color: #0056b3; }
                    .footer { margin-top: 30px; font-size: 12px; color: #666; }
                    .info-box { background-color: #f8f9fa; padding: 15px; border-radius: 4px; margin: 15px 0; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <h2>You've Been Invited!</h2>
                    <p>Hello,</p>
                    <p><strong>%s</strong> has invited you to join <strong>%s</strong> as a <strong>%s</strong>.</p>
                    <div class="info-box">
                      <p><strong>Invited by:</strong> %s</p>
                      <p><strong>Company:</strong> %s</p>
                      <p><strong>Role:</strong> %s</p>
                      <p><strong>Expires:</strong> %s</p>
                    </div>
                    <p>Click the button below to accept the invitation and create your account:</p>
                    <a href="%s" class="button">Accept Invitation</a>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #007bff;">%s</p>
                    <p>This invitation will expire on %s.</p>
                    <p>If you didn't expect this invitation, please ignore this email.</p>
                    <div class="footer">
                      <p>This is an automated message. Please do not reply to this email.</p>
                    </div>
                  </div>
                </body>
                </html>
                """,
            inviterName,
            companyName,
            role,
            inviterName,
            companyName,
            role,
            expirationDate,
            invitationUrl,
            invitationUrl,
            expirationDate);

    try {
      logger.debug("Sending invitation email to {} from {}", toEmail, fromEmail);
      CreateEmailOptions emailOptions =
          CreateEmailOptions.builder()
              .from(fromEmail)
              .to(toEmail)
              .subject(String.format("Invitation to join %s", companyName))
              .html(htmlContent)
              .build();

      CreateEmailResponse response = resend.emails().send(emailOptions);
      logger.info("Invitation email sent successfully to {} with ID: {}", toEmail, response.getId());
    } catch (ResendException e) {
      logger.error(
          "Failed to send invitation email to {} from {}: {}",
          toEmail,
          fromEmail,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to send invitation email", e);
    }
  }
}
