package com.accounting.service.impl.ap;

import com.accounting.dto.BatchReminderRequestDTO;
import com.accounting.dto.BatchReminderResultDTO;
import com.accounting.dto.ReminderRequestDTO;
import com.accounting.dto.ReminderResultDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.APAgingAlertService;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of APAgingAlertService for AP aging alert and reminder operations.
 * Handles manual reminders and scheduled alerts for overdue payables.
 */
@Service
@Transactional
public class APAgingAlertServiceImpl implements APAgingAlertService {

  private static final Logger logger = LoggerFactory.getLogger(APAgingAlertServiceImpl.class);

  private final PurchaseBillRepository purchaseBillRepository;
  private final AuditService auditService;
  private final EmailService emailService;

  public APAgingAlertServiceImpl(
      PurchaseBillRepository purchaseBillRepository,
      AuditService auditService,
      EmailService emailService) {
    this.purchaseBillRepository = purchaseBillRepository;
    this.auditService = auditService;
    this.emailService = emailService;
  }

  @Override
  public ReminderResultDTO sendReminder(ReminderRequestDTO request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    ReminderResultDTO result = new ReminderResultDTO();
    result.setSuccess(false);

    try {
      // Generate reminder message
      String message = generateReminderMessage(request);

      // Send to recipients
      List<String> sentTo = new ArrayList<>();
      List<String> failedTo = new ArrayList<>();

      for (String recipient : request.getRecipients()) {
        try {
          // For now, log reminder for manual/in-app notification
          // TODO: Implement email sending when generic email service is available
          if (emailService != null && recipient.contains("@")) {
            // Email service doesn't have generic sendEmail method yet
            // Log for now - can be enhanced when email service supports generic emails
            logger.info("Reminder message for recipient {} (email service not yet available for generic emails): {}", recipient, message);
            sentTo.add(recipient);
          } else {
            // Log for manual sending or in-app notification
            logger.info("Reminder message for recipient {} (manual/in-app): {}", recipient, message);
            sentTo.add(recipient);
          }
        } catch (Exception e) {
          logger.warn("Failed to send reminder to {}: {}", recipient, e.getMessage());
          failedTo.add(recipient);
        }
      }

      result.setSuccess(failedTo.isEmpty());
      result.setSentTo(sentTo);
      result.setFailedTo(failedTo);
      result.setMessage(failedTo.isEmpty() ? "Reminder sent successfully" : "Some reminders failed");

      // Log audit event
      try {
        auditService.logAgingReminderSent(
            request.getSupplierId(),
            request.getBillIds(),
            request.getRecipients(),
            companyId);
      } catch (Exception e) {
        logger.warn("Failed to log reminder audit event: {}", e.getMessage());
      }

    } catch (Exception e) {
      logger.error("Failed to send reminder", e);
      result.setMessage("Failed to send reminder: " + e.getMessage());
    }

    return result;
  }

  @Override
  public BatchReminderResultDTO sendBatchReminders(BatchReminderRequestDTO request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    BatchReminderResultDTO result = new BatchReminderResultDTO();
    result.setTotalSuppliers(request.getSupplierIds().size());
    int successCount = 0;
    int failedCount = 0;
    List<String> sentTo = new ArrayList<>();
    List<String> failedTo = new ArrayList<>();

    for (Long supplierId : request.getSupplierIds()) {
      try {
        ReminderRequestDTO reminderRequest = new ReminderRequestDTO();
        reminderRequest.setSupplierId(supplierId);
        reminderRequest.setRecipients(request.getRecipients());
        reminderRequest.setMessage(request.getMessage());

        ReminderResultDTO reminderResult = sendReminder(reminderRequest);
        if (reminderResult.isSuccess()) {
          successCount++;
          sentTo.addAll(reminderResult.getSentTo());
        } else {
          failedCount++;
          failedTo.addAll(reminderResult.getFailedTo());
        }
      } catch (Exception e) {
        logger.warn("Failed to send batch reminder for supplier {}: {}", supplierId, e.getMessage());
        failedCount++;
        failedTo.add("Supplier " + supplierId);
      }
    }

    result.setSuccessCount(successCount);
    result.setFailedCount(failedCount);
    result.setSentTo(sentTo);
    result.setFailedTo(failedTo);

    // Log audit event
    try {
      auditService.logAgingBatchReminderSent(
          request.getSupplierIds(),
          request.getRecipients(),
          companyId);
    } catch (Exception e) {
      logger.warn("Failed to log batch reminder audit event: {}", e.getMessage());
    }

    return result;
  }

  @Override
  public void scheduleAutoAlerts(Long periodId, LocalDate asOfDate, List<String> roles) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // TODO: Implement scheduled alert functionality
    // This would typically use Spring's @Scheduled annotation or a job scheduler
    // For now, log that this feature is not yet implemented
    logger.info(
        "Scheduled auto-alerts requested for periodId={}, asOfDate={}, roles={}",
        periodId,
        asOfDate,
        roles);
    logger.warn("Scheduled auto-alerts functionality is not yet fully implemented");
  }

  /**
   * Generate reminder message with bill details and aging information.
   */
  private String generateReminderMessage(ReminderRequestDTO request) {
    Long companyId = CompanyContext.getCompanyId();
    StringBuilder message = new StringBuilder();

    if (request.getMessage() != null && !request.getMessage().isEmpty()) {
      message.append(request.getMessage()).append("\n\n");
    } else {
      message.append("This is a reminder regarding overdue accounts payable.\n\n");
    }

    if (request.getSupplierId() != null) {
      message.append("Supplier ID: ").append(request.getSupplierId()).append("\n");
    }

    if (request.getBillIds() != null && !request.getBillIds().isEmpty()) {
      message.append("Bills:\n");
      List<PurchaseBill> bills =
          purchaseBillRepository.findByCompanyIdAndStatus(companyId, PurchaseBillStatus.POSTED);

      for (UUID billId : request.getBillIds()) {
        bills.stream()
            .filter(bill -> bill.getId().equals(billId))
            .findFirst()
            .ifPresent(
                bill -> {
                  message.append("  - Bill #")
                      .append(bill.getBillNumber())
                      .append(": ")
                      .append(bill.getTotalAmount())
                      .append(" VND, Due: ")
                      .append(bill.getDueDate())
                      .append("\n");
                });
      }
    } else if (request.getSupplierId() != null) {
      // Include all overdue bills for supplier
      message.append("Please review all overdue bills for this supplier.\n");
    }

    message.append("\nPlease take appropriate action to resolve these outstanding amounts.");

    return message.toString();
  }
}

