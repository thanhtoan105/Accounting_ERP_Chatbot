package com.accounting.service;

import com.accounting.entity.ARStatementHistory;

/**
 * Service interface for AR statement email delivery.
 */
public interface ARStatementEmailService {

  /**
   * Send statement to customer via email.
   *
   * @param customerId customer ID
   * @param recipientEmail recipient email address
   * @param statement statement DTO (summary or detailed)
   * @param statementFormat statement format (SUMMARY or DETAILED)
   */
  void sendStatementEmail(
      Long customerId,
      String recipientEmail,
      Object statement,
      ARStatementHistory.StatementFormat statementFormat);
}

