package com.accounting.service.impl.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.report.ReportDownloadLink;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.entity.report.RecipientAccessStatus;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.entity.report.ReportScheduleRun;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.UserRepository;
import com.accounting.service.AuditService;
import com.accounting.service.CompanyService;
import com.accounting.service.ReportDistributionService;
import com.accounting.service.ReportPasswordStrategy;
import com.accounting.service.StorageService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@Service
public class ReportDistributionServiceImpl implements ReportDistributionService {

  private static final Logger logger = LoggerFactory.getLogger(ReportDistributionServiceImpl.class);
  private static final int MAX_TTL_SECONDS = 7 * 24 * 60 * 60;
  private static final int DEFAULT_TTL_SECONDS = 3 * 24 * 60 * 60;

  private final StorageService storageService;
  private final CompanyService companyService;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final RecipientAccessValidator recipientAccessValidator;
  private final ReportPasswordStrategy reportPasswordStrategy;
  private final AccountingPeriodRepository accountingPeriodRepository;
  private final Resend resend;
  private final String fromEmail;
  private final int signedUrlTtlSeconds;

  public ReportDistributionServiceImpl(
      StorageService storageService,
      CompanyService companyService,
      UserRepository userRepository,
      AuditService auditService,
      RecipientAccessValidator recipientAccessValidator,
      ReportPasswordStrategy reportPasswordStrategy,
      AccountingPeriodRepository accountingPeriodRepository,
      @Value("${resend.api-key:#{null}}") String apiKey,
      @Value("${resend.from-email:onboarding@resend.dev}") String fromEmail,
      @Value("${report.distribution.signed-url-ttl-seconds:259200}") int signedUrlTtlSeconds) {
    this.storageService = storageService;
    this.companyService = companyService;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.recipientAccessValidator = recipientAccessValidator;
    this.reportPasswordStrategy = reportPasswordStrategy;
    this.accountingPeriodRepository = accountingPeriodRepository;
    this.fromEmail = fromEmail;
    this.signedUrlTtlSeconds = Math.min(signedUrlTtlSeconds, MAX_TTL_SECONDS);

    if (apiKey == null || apiKey.isBlank()) {
      logger.warn("Resend API key not configured. Email sending will be disabled.");
      this.resend = null;
    } else {
      this.resend = new Resend(apiKey);
    }
  }

  @Override
  public ReportDownloadLink uploadAndGetSignedUrl(byte[] content, String filename, String format) {
    String hash = calculateSha256(content);
    String storagePath = "reports/" + UUID.randomUUID() + "/" + filename;

    uploadToStorage(storagePath, content);

    int ttlSeconds = Math.min(signedUrlTtlSeconds, MAX_TTL_SECONDS);
    if (ttlSeconds <= 0) {
      ttlSeconds = DEFAULT_TTL_SECONDS;
    }

    String signedUrl = storageService.generateSignedUrl(storagePath, ttlSeconds);

    return ReportDownloadLink.builder()
        .format(format)
        .url(signedUrl)
        .expiresAt(Instant.now().plusSeconds(ttlSeconds))
        .hash(hash)
        .build();
  }

  @Override
  public void sendScheduledReportEmail(
      ReportSchedule schedule,
      ReportScheduleRun run,
      List<ReportDownloadLink> downloadLinks,
      byte[] pdfAttachment) {
    if (resend == null) {
      logger.warn(
          "Email sending skipped - Resend API key not configured. Schedule: {}",
          schedule.getName());
      return;
    }

    Company company = companyService.getCurrentCompanySettings();
    String htmlContent = buildScheduledReportEmailHtml(schedule, run, downloadLinks, company);

    List<String> recipients = schedule.getRecipients();
    if (recipients == null || recipients.isEmpty()) {
      logger.warn("No recipients configured for schedule: {}", schedule.getName());
      return;
    }

    // Validate recipient access before sending
    Map<String, RecipientAccessStatus> accessStatuses =
        recipientAccessValidator.validateAllRecipients(recipients, schedule.getReportType());

    // Apply password protection to PDF if attachment exists
    byte[] protectedPdf = null;
    String passwordHint = null;
    if (pdfAttachment != null && pdfAttachment.length > 0) {
      AccountingPeriod period = run.getPeriodId() != null
          ? accountingPeriodRepository.findById(run.getPeriodId()).orElse(null)
          : null;
      if (period != null) {
        String password = reportPasswordStrategy.generatePassword(company, period);
        protectedPdf = applyPasswordProtection(pdfAttachment, password);
        passwordHint = "Last 4 digits of company tax code + period month/year (MMYYYY)";
      } else {
        protectedPdf = pdfAttachment;
      }
    }

    for (String recipient : recipients) {
      RecipientAccessStatus accessStatus = accessStatuses.getOrDefault(
          recipient, RecipientAccessStatus.ACCESS_DENIED);

      if (accessStatus == RecipientAccessStatus.ACCESS_DENIED) {
        logger.warn(
            "Skipping recipient {} for schedule {} - access denied",
            recipient,
            schedule.getName());
        auditService.logReportScheduleRunEvent(
            "REPORT_EMAIL_BLOCKED", run.getId(), schedule.getName(), "ACCESS_DENIED: " + recipient);
        continue;
      }

      try {
        String emailContent = htmlContent;
        if (passwordHint != null) {
          emailContent = htmlContent.replace(
              "</body>",
              "<p style=\"color: #6b7280; font-size: 12px;\">PDF Password: " + passwordHint + "</p></body>");
        }

        sendEmailToRecipient(
            recipient,
            String.format("[%s] Scheduled Report: %s", company.getName(), schedule.getName()),
            emailContent,
            protectedPdf,
            schedule.getName() + ".pdf");

        auditService.logReportScheduleRunEvent(
            "REPORT_EMAIL_SENT", run.getId(), schedule.getName(), run.getTriggerType().name());

        logger.info(
            "Scheduled report email sent to {} for schedule: {}", recipient, schedule.getName());
      } catch (Exception e) {
        logger.error(
            "Failed to send scheduled report email to {} for schedule: {}",
            recipient,
            schedule.getName(),
            e);
        auditService.logReportScheduleRunEvent(
            "REPORT_EMAIL_FAILED", run.getId(), schedule.getName(), run.getTriggerType().name());
      }
    }
  }

  @Override
  public void sendFailureNotification(ReportSchedule schedule, ReportScheduleRun run) {
    if (resend == null) {
      logger.warn(
          "Email sending skipped - Resend API key not configured. Failure notification for: {}",
          schedule.getName());
      return;
    }

    User owner =
        userRepository
            .findById(schedule.getOwnerId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Owner not found for schedule: " + schedule.getId()));

    Company company = companyService.getCurrentCompanySettings();
    String htmlContent = buildFailureEmailHtml(schedule, run, company);

    try {
      sendEmailToRecipient(
          owner.getEmail(),
          String.format("[Alert] Report Schedule Failed: %s", schedule.getName()),
          htmlContent,
          null,
          null);

      logger.info(
          "Failure notification sent to {} for schedule: {}", owner.getEmail(), schedule.getName());
    } catch (Exception e) {
      logger.error(
          "Failed to send failure notification to {} for schedule: {}",
          owner.getEmail(),
          schedule.getName(),
          e);
    }
  }

  private void uploadToStorage(String storagePath, byte[] content) {
    UUID fakeId = UUID.randomUUID();
    MultipartFile multipartFile = new ByteArrayMultipartFile(content, storagePath, "application/octet-stream");
    storageService.uploadVoucherAttachment(fakeId, multipartFile);
  }

  private static class ByteArrayMultipartFile implements MultipartFile {
    private final byte[] content;
    private final String name;
    private final String contentType;

    ByteArrayMultipartFile(byte[] content, String name, String contentType) {
      this.content = content;
      this.name = name;
      this.contentType = contentType;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return name;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() throws IOException {
      return content;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
      java.nio.file.Files.write(dest.toPath(), content);
    }
  }

  private void sendEmailToRecipient(
      String recipient,
      String subject,
      String htmlContent,
      byte[] pdfAttachment,
      String attachmentFilename) {
    try {
      CreateEmailOptions.Builder emailBuilder =
          CreateEmailOptions.builder().from(fromEmail).to(recipient).subject(subject).html(htmlContent);

      if (pdfAttachment != null && pdfAttachment.length > 0 && attachmentFilename != null) {
        String base64Content = Base64.getEncoder().encodeToString(pdfAttachment);
        Attachment attachment =
            Attachment.builder().fileName(attachmentFilename).content(base64Content).build();
        emailBuilder.attachments(new Attachment[] {attachment});
      }

      CreateEmailResponse response = resend.emails().send(emailBuilder.build());
      logger.debug("Email sent to {} with ID: {}", recipient, response.getId());
    } catch (ResendException e) {
      throw new RuntimeException("Failed to send email to: " + recipient, e);
    }
  }

  private String buildScheduledReportEmailHtml(
      ReportSchedule schedule,
      ReportScheduleRun run,
      List<ReportDownloadLink> downloadLinks,
      Company company) {
    StringBuilder linksHtml = new StringBuilder();
    DateTimeFormatter expiryFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    for (ReportDownloadLink link : downloadLinks) {
      linksHtml.append(
          String.format(
              """
              <tr>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">%s</td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">
                  <a href="%s" style="color: #2563eb; text-decoration: none;">Download</a>
                </td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb; font-size: 12px; color: #6b7280;">%s</td>
              </tr>
              """,
              link.getFormat(),
              link.getUrl(),
              expiryFormatter.format(link.getExpiresAt())));
    }

    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #374151; margin: 0; padding: 0; background-color: #f9fafb;">
          <div style="max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); overflow: hidden;">
            <div style="background: linear-gradient(135deg, #10b981 0%%, #059669 100%%); padding: 32px 24px; text-align: center;">
              <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">Scheduled Report Ready</h1>
              <p style="color: #d1fae5; margin: 8px 0 0 0; font-size: 14px;">%s</p>
            </div>
            <div style="padding: 32px 24px;">
              <p style="margin: 0 0 16px 0; font-size: 16px;">Your scheduled report <strong>%s</strong> has been generated.</p>
              <div style="background-color: #f3f4f6; padding: 16px; border-radius: 6px; margin: 16px 0;">
                <p style="margin: 0; font-size: 14px;"><strong>Report Type:</strong> %s</p>
                <p style="margin: 8px 0 0 0; font-size: 14px;"><strong>Period:</strong> %s</p>
              </div>
              <h3 style="margin: 24px 0 12px 0; font-size: 16px;">Download Links</h3>
              <table style="width: 100%%; border-collapse: collapse; border: 1px solid #e5e7eb; border-radius: 6px;">
                <thead>
                  <tr style="background-color: #f3f4f6;">
                    <th style="padding: 12px; text-align: left; font-size: 12px; font-weight: 600; color: #6b7280;">Format</th>
                    <th style="padding: 12px; text-align: left; font-size: 12px; font-weight: 600; color: #6b7280;">Link</th>
                    <th style="padding: 12px; text-align: left; font-size: 12px; font-weight: 600; color: #6b7280;">Expires</th>
                  </tr>
                </thead>
                <tbody>
                  %s
                </tbody>
              </table>
              <div style="background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 12px; margin: 24px 0; border-radius: 4px;">
                <p style="margin: 0; font-size: 13px; color: #92400e;">
                  <strong>Note:</strong> Download links expire after the date shown. Please download promptly.
                </p>
              </div>
            </div>
            <div style="background-color: #f9fafb; padding: 24px; text-align: center; border-top: 1px solid #e5e7eb;">
              <p style="margin: 0; font-size: 12px; color: #9ca3af;">
                Automated report from %s
              </p>
            </div>
          </div>
        </body>
        </html>
        """,
        company.getName(),
        schedule.getName(),
        schedule.getReportType(),
        run.getPeriodLabel() != null ? run.getPeriodLabel() : "N/A",
        linksHtml.toString(),
        company.getName());
  }

  private String buildFailureEmailHtml(
      ReportSchedule schedule, ReportScheduleRun run, Company company) {
    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #374151; margin: 0; padding: 0; background-color: #f9fafb;">
          <div style="max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); overflow: hidden;">
            <div style="background: linear-gradient(135deg, #ef4444 0%%, #dc2626 100%%); padding: 32px 24px; text-align: center;">
              <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 600;">Report Schedule Failed</h1>
              <p style="color: #fecaca; margin: 8px 0 0 0; font-size: 14px;">%s</p>
            </div>
            <div style="padding: 32px 24px;">
              <p style="margin: 0 0 16px 0; font-size: 16px;">The scheduled report <strong>%s</strong> failed to generate.</p>
              <div style="background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 16px; margin: 16px 0; border-radius: 4px;">
                <p style="margin: 0 0 8px 0; font-weight: 600; color: #991b1b; font-size: 14px;">Error Details:</p>
                <p style="margin: 0; font-size: 13px; color: #7f1d1d;">
                  <strong>Error Code:</strong> %s<br>
                  <strong>Message:</strong> %s<br>
                  <strong>Attempt:</strong> %d of %d
                </p>
              </div>
              <div style="background-color: #f3f4f6; padding: 16px; border-radius: 6px; margin: 16px 0;">
                <p style="margin: 0; font-size: 14px;"><strong>Schedule:</strong> %s</p>
                <p style="margin: 8px 0 0 0; font-size: 14px;"><strong>Report Type:</strong> %s</p>
                <p style="margin: 8px 0 0 0; font-size: 14px;"><strong>Run ID:</strong> %s</p>
              </div>
              <p style="margin: 24px 0 0 0; font-size: 14px; color: #6b7280;">
                Please review the schedule configuration and try again. If the issue persists, contact support.
              </p>
            </div>
            <div style="background-color: #f9fafb; padding: 24px; text-align: center; border-top: 1px solid #e5e7eb;">
              <p style="margin: 0; font-size: 12px; color: #9ca3af;">
                Automated alert from %s
              </p>
            </div>
          </div>
        </body>
        </html>
        """,
        company.getName(),
        schedule.getName(),
        run.getErrorCode() != null ? run.getErrorCode() : "UNKNOWN",
        run.getErrorMessage() != null ? run.getErrorMessage() : "No details available",
        run.getAttempt(),
        run.getMaxAttempts(),
        schedule.getName(),
        schedule.getReportType(),
        run.getId().toString(),
        company.getName());
  }

  private String calculateSha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content);
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Apply password protection to a PDF document using OpenPDF.
   * 
   * @param pdfContent original PDF content
   * @param password password to apply
   * @return password-protected PDF content
   */
  private byte[] applyPasswordProtection(byte[] pdfContent, String password) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PdfReader reader = new PdfReader(pdfContent);
      PdfStamper stamper = new PdfStamper(reader, outputStream);
      stamper.setEncryption(
          password.getBytes(),
          password.getBytes(),
          PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_COPY,
          PdfWriter.ENCRYPTION_AES_128);
      stamper.close();
      reader.close();
      return outputStream.toByteArray();
    } catch (Exception e) {
      logger.error("Failed to apply password protection to PDF", e);
      return pdfContent;
    }
  }
}
