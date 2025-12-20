package com.accounting.service;

import java.util.List;

import com.accounting.dto.report.ReportDownloadLink;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.entity.report.ReportScheduleRun;

import jakarta.servlet.http.HttpServletRequest;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);

    /**
     * Send invitation email to user.
     *
     * @param toEmail         invitee email address
     * @param invitationToken invitation token for registration link
     * @param inviterName     name of user who sent the invitation
     * @param companyName     company name
     * @param role            role assigned to user
     * @param expiresAt       invitation expiration date/time
     * @param request         HTTP request (for logging purposes)
     */
    void sendInvitationEmail(
            String toEmail,
            String invitationToken,
            String inviterName,
            String companyName,
            String role,
            java.time.Instant expiresAt,
            HttpServletRequest request);

    /**
     * Send AR reminder email to customer for overdue invoices.
     *
     * @param toEmail      customer email address
     * @param customerName customer name
     * @param companyName  company name
     * @param invoices     list of overdue invoices (invoice number, due date,
     *                     amount, days overdue)
     * @param totalAmount  total amount due
     */
    void sendARReminderEmail(
            String toEmail,
            String customerName,
            String companyName,
            java.util.List<OverdueInvoiceInfo> invoices,
            java.math.BigDecimal totalAmount);

    /** DTO for overdue invoice information in reminder emails */
    record OverdueInvoiceInfo(
            String invoiceNumber,
            java.time.LocalDate dueDate,
            java.math.BigDecimal amount,
            int daysOverdue) {
    }

    /**
     * Send AR statement email to customer with PDF attachment.
     *
     * @param toEmail      customer email address
     * @param customerName customer name
     * @param companyName  company name
     * @param statementPdf PDF content as byte array
     * @param fileName     attachment file name (e.g.,
     *                     "Statement_CUST001_2025-11-22.pdf")
     */
    void sendARStatementEmail(
            String toEmail,
            String customerName,
            String companyName,
            byte[] statementPdf,
            String fileName);

    /**
     * Send scheduled report email to recipient with download links and optional PDF attachment.
     *
     * @param recipientEmail recipient email address
     * @param schedule       the report schedule
     * @param run            the schedule run instance
     * @param downloadLinks  list of download links for each export format
     * @param pdfAttachment  optional password-protected PDF attachment (can be null)
     */
    void sendScheduledReportEmail(
            String recipientEmail,
            ReportSchedule schedule,
            ReportScheduleRun run,
            List<ReportDownloadLink> downloadLinks,
            byte[] pdfAttachment);

    /**
     * Send failure notification to schedule owner when a run fails.
     *
     * @param schedule the report schedule that failed
     * @param run      the failed run instance
     */
    void sendScheduleFailureNotification(
            ReportSchedule schedule,
            ReportScheduleRun run);
}
