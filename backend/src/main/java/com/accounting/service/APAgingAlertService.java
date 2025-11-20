package com.accounting.service;

import com.accounting.dto.BatchReminderRequestDTO;
import com.accounting.dto.BatchReminderResultDTO;
import com.accounting.dto.ReminderRequestDTO;
import com.accounting.dto.ReminderResultDTO;
import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for AP aging alert and reminder operations.
 * Handles manual reminders and scheduled alerts for overdue payables.
 */
public interface APAgingAlertService {

  /**
   * Send reminder for specific supplier and bills.
   *
   * @param request reminder request with supplier ID, bill IDs, recipients, and optional message
   * @return reminder result with success status and recipient information
   */
  ReminderResultDTO sendReminder(ReminderRequestDTO request);

  /**
   * Send batch reminders for multiple suppliers.
   *
   * @param request batch reminder request with supplier IDs, recipients, and optional message
   * @return batch reminder result with success/failure counts
   */
  BatchReminderResultDTO sendBatchReminders(BatchReminderRequestDTO request);

  /**
   * Schedule automatic alerts for relevant roles.
   * Alerts are sent based on configured schedules and thresholds.
   *
   * @param periodId period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @param roles list of roles to notify (optional, uses default if not provided)
   */
  void scheduleAutoAlerts(Long periodId, LocalDate asOfDate, List<String> roles);
}

