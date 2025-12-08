package com.accounting.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accounting.service.EmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import jakarta.servlet.http.HttpServletRequest;

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
    // Clean frontend URL - remove any comments or whitespace that might have been
    // included
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
        logger.error(
            "Invalid 'from' field format. Current value: '{}'. Expected format: 'email@example.com' or 'Name <email@example.com>'",
            fromEmail);
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

    String invitationUrl = String.format("%s/invite/%s", frontendUrl, invitationToken);

    // Format expiration date
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault());
    String expirationDate = formatter.format(expiresAt);

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
      CreateEmailOptions emailOptions = CreateEmailOptions.builder()
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

  @Override
  public void sendARReminderEmail(
      String toEmail,
      String customerName,
      String companyName,
      java.util.List<EmailService.OverdueInvoiceInfo> invoices,
      java.math.BigDecimal totalAmount) {
    if (resend == null) {
      logger.warn(
          "Email sending skipped - Resend API key not configured. AR reminder for {}",
          toEmail);
      return;
    }

    // Build invoice table rows
    StringBuilder invoiceRows = new StringBuilder();
    java.text.NumberFormat currencyFormat = java.text.NumberFormat
        .getCurrencyInstance(new java.util.Locale("vi", "VN"));
    java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    for (EmailService.OverdueInvoiceInfo invoice : invoices) {
      String formattedAmount = currencyFormat.format(invoice.amount());
      String formattedDate = invoice.dueDate().format(dateFormatter);
      String overdueClass = invoice.daysOverdue() > 30 ? "color: #dc2626;" : "color: #ea580c;";

      invoiceRows.append(
          String.format(
              """
                      <tr>
                        <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">%s</td>
                        <td style="padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: right;">%s</td>
                        <td style="padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: center; font-weight: 600; %s">%d days</td>
                      </tr>
                  """,
              invoice.invoiceNumber(),
              formattedDate,
              formattedAmount,
              overdueClass,
              invoice.daysOverdue()));
    }

    String formattedTotal = currencyFormat.format(totalAmount);

    String htmlContent = String.format(
        """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #374151; margin: 0; padding: 0; background-color: #f9fafb;">
              <div style="max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); padding: 32px 24px; text-align: center;">
                  <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">Payment Reminder</h1>
                  <p style="color: #e0e7ff; margin: 8px 0 0 0; font-size: 14px;">%s</p>
                </div>

                <!-- Content -->
                <div style="padding: 32px 24px;">
                  <p style="margin: 0 0 24px 0; font-size: 16px;">Dear %s,</p>

                  <p style="margin: 0 0 24px 0; font-size: 14px; color: #6b7280;">
                    This is a friendly reminder that you have outstanding invoices with us. We kindly request your prompt attention to settle the following amounts:
                  </p>

                  <!-- Invoices Table -->
                  <div style="margin: 24px 0; border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden;">
                    <table style="width: 100%%; border-collapse: collapse;">
                      <thead>
                        <tr style="background-color: #f3f4f6;">
                          <th style="padding: 12px; text-align: left; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; border-bottom: 2px solid #e5e7eb;">Invoice #</th>
                          <th style="padding: 12px; text-align: left; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; border-bottom: 2px solid #e5e7eb;">Due Date</th>
                          <th style="padding: 12px; text-align: right; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; border-bottom: 2px solid #e5e7eb;">Amount</th>
                          <th style="padding: 12px; text-align: center; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; border-bottom: 2px solid #e5e7eb;">Overdue</th>
                        </tr>
                      </thead>
                      <tbody>
                        %s
                      </tbody>
                      <tfoot>
                        <tr style="background-color: #fef3c7; font-weight: 600;">
                          <td colspan="2" style="padding: 16px; font-size: 14px;">Total Amount Due</td>
                          <td colspan="2" style="padding: 16px; text-align: right; font-size: 16px; color: #dc2626;">%s</td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>

                  <!-- Payment Instructions -->
                  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 16px; margin: 24px 0; border-radius: 4px;">
                    <p style="margin: 0 0 8px 0; font-weight: 600; color: #1e40af; font-size: 14px;">Payment Instructions:</p>
                    <p style="margin: 0; font-size: 13px; color: #1e3a8a;">
                      Please arrange payment at your earliest convenience. If you have already made the payment, please disregard this reminder and accept our thanks.
                    </p>
                  </div>

                  <p style="margin: 24px 0 0 0; font-size: 14px; color: #6b7280;">
                    If you have any questions or concerns regarding these invoices, please don't hesitate to contact us.
                  </p>

                  <p style="margin: 24px 0 0 0; font-size: 14px;">
                    Best regards,<br>
                    <strong>%s</strong>
                  </p>
                </div>

                <!-- Footer -->
                <div style="background-color: #f9fafb; padding: 24px; text-align: center; border-top: 1px solid #e5e7eb;">
                  <p style="margin: 0; font-size: 12px; color: #9ca3af;">
                    This is an automated reminder from %s<br>
                    Please do not reply to this email.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """,
        companyName,
        customerName,
        invoiceRows.toString(),
        formattedTotal,
        companyName,
        companyName);

    try {
      logger.debug("Sending AR reminder email to {} from {}", toEmail, fromEmail);
      CreateEmailOptions emailOptions = CreateEmailOptions.builder()
          .from(fromEmail)
          .to(toEmail)
          .subject(String.format("Payment Reminder - %s", companyName))
          .html(htmlContent)
          .build();

      CreateEmailResponse response = resend.emails().send(emailOptions);
      logger.info(
          "AR reminder email sent successfully to {} with ID: {} ({} invoices, total: {})",
          toEmail,
          response.getId(),
          invoices.size(),
          totalAmount);
    } catch (ResendException e) {
      logger.error(
          "Failed to send AR reminder email to {} from {}: {}",
          toEmail,
          fromEmail,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to send AR reminder email", e);
    }
  }

  @Override
  public void sendARStatementEmail(
      String toEmail,
      String customerName,
      String companyName,
      byte[] statementPdf,
      String fileName) {
    if (resend == null) {
      logger.warn(
          "Email sending skipped - Resend API key not configured. AR statement for {}",
          toEmail);
      return;
    }

    String htmlContent = String.format(
        """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #374151; margin: 0; padding: 0; background-color: #f9fafb;">
              <div style="max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); padding: 32px 24px; text-align: center;">
                  <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">Account Statement</h1>
                  <p style="color: #e0e7ff; margin: 8px 0 0 0; font-size: 14px;">%s</p>
                </div>

                <!-- Content -->
                <div style="padding: 32px 24px;">
                  <p style="margin: 0 0 24px 0; font-size: 16px;">Dear %s,</p>

                  <p style="margin: 0 0 24px 0; font-size: 14px; color: #6b7280;">
                    Please find attached your account statement as of the statement date. This statement includes all invoices, payments, and outstanding balances.
                  </p>

                  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 16px; margin: 24px 0; border-radius: 4px;">
                    <p style="margin: 0; font-size: 13px; color: #1e3a8a;">
                      If you have any questions or concerns regarding this statement, please don't hesitate to contact us.
                    </p>
                  </div>

                  <p style="margin: 24px 0 0 0; font-size: 14px;">
                    Best regards,<br>
                    <strong>%s</strong>
                  </p>
                </div>

                <!-- Footer -->
                <div style="background-color: #f9fafb; padding: 24px; text-align: center; border-top: 1px solid #e5e7eb;">
                  <p style="margin: 0; font-size: 12px; color: #9ca3af;">
                    This is an automated message from %s<br>
                    Please do not reply to this email.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """,
        companyName,
        customerName,
        companyName,
        companyName);

    try {
      logger.debug("Sending AR statement email to {} from {}", toEmail, fromEmail);

      // Create email builder
      CreateEmailOptions.Builder emailBuilder = CreateEmailOptions.builder()
          .from(fromEmail)
          .to(toEmail)
          .subject(String.format("Account Statement - %s", companyName))
          .html(htmlContent);

      // Add PDF attachment using Resend attachment API
      // According to Resend docs: content should be base64-encoded string
      if (statementPdf != null && statementPdf.length > 0) {
        String base64Content = java.util.Base64.getEncoder().encodeToString(statementPdf);
        Attachment attachment = Attachment.builder()
            .fileName(fileName)
            .content(base64Content)
            .build();
        emailBuilder.attachments(new Attachment[] { attachment });
        logger.debug("Added PDF attachment: {} ({} bytes, base64 length: {})", fileName, statementPdf.length,
            base64Content.length());
      }

      CreateEmailResponse response = resend.emails().send(emailBuilder.build());
      logger.info(
          "AR statement email sent successfully to {} with ID: {} (attachment: {})",
          toEmail,
          response.getId(),
          fileName);
    } catch (ResendException e) {
      logger.error(
          "Failed to send AR statement email to {} from {}: {}",
          toEmail,
          fromEmail,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to send AR statement email", e);
    }
  }

  @Override
  public void sendScheduledReportEmail(
      String recipientEmail,
      com.accounting.entity.report.ReportSchedule schedule,
      com.accounting.entity.report.ReportScheduleRun run,
      java.util.List<com.accounting.dto.report.ReportDownloadLink> downloadLinks,
      byte[] pdfAttachment) {
    // Delegate to ReportDistributionService for scheduled report emails
    // This method exists for interface compliance; actual implementation is in ReportDistributionServiceImpl
    logger.warn(
        "sendScheduledReportEmail called on EmailServiceImpl - this should be handled by ReportDistributionService. Recipient: {}, Schedule: {}",
        recipientEmail,
        schedule != null ? schedule.getName() : "null");
  }

  @Override
  public void sendScheduleFailureNotification(
      com.accounting.entity.report.ReportSchedule schedule,
      com.accounting.entity.report.ReportScheduleRun run) {
    // Delegate to ReportDistributionService for failure notifications
    // This method exists for interface compliance; actual implementation is in ReportDistributionServiceImpl
    logger.warn(
        "sendScheduleFailureNotification called on EmailServiceImpl - this should be handled by ReportDistributionService. Schedule: {}",
        schedule != null ? schedule.getName() : "null");
  }
}
