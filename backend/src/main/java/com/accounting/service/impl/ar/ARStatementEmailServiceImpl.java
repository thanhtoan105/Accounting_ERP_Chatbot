package com.accounting.service.impl.ar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accounting.dto.ARStatementDetailedDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import com.accounting.entity.ARStatementHistory;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARStatementEmailService;
import com.accounting.service.ARStatementExportService;
import com.accounting.service.EmailService;

/**
 * Implementation of ARStatementEmailService for email delivery.
 */
@Service
public class ARStatementEmailServiceImpl implements ARStatementEmailService {

  private static final Logger logger = LoggerFactory.getLogger(ARStatementEmailServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final EmailService emailService;
  private final ARStatementExportService exportService;
  private final CustomerRepository customerRepository;
  private final CompanyRepository companyRepository;

  public ARStatementEmailServiceImpl(
      EmailService emailService,
      ARStatementExportService exportService,
      CustomerRepository customerRepository,
      CompanyRepository companyRepository) {
    this.emailService = emailService;
    this.exportService = exportService;
    this.customerRepository = customerRepository;
    this.companyRepository = companyRepository;
  }

  @Override
  public void sendStatementEmail(
      Long customerId,
      String recipientEmail,
      Object statement,
      ARStatementHistory.StatementFormat statementFormat) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    try {
      // Get customer and company info
      Customer customer =
          customerRepository
              .findByCompanyIdAndId(companyId, customerId)
              .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

      Company company =
          companyRepository
              .findById(companyId)
              .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));

      // Generate PDF
      byte[] statementPdf = exportService.exportStatement(statement, "PDF", statementFormat);

      // Generate filename
      String customerCode = customer.getCode() != null ? customer.getCode() : "CUST" + customerId;
      LocalDate asOfDate =
          statement instanceof ARStatementSummaryDTO
              ? ((ARStatementSummaryDTO) statement).getAsOfDate()
              : ((ARStatementDetailedDTO) statement).getAsOfDate();
      String fileName =
          String.format(
              "Statement_%s_%s.pdf",
              customerCode,
              asOfDate != null ? DATE_FORMATTER.format(asOfDate) : DATE_FORMATTER.format(LocalDate.now()));

      // Send email with PDF attachment
      emailService.sendARStatementEmail(
          recipientEmail,
          customer.getName(),
          company.getName(),
          statementPdf,
          fileName);

      logger.info(
          "AR statement email sent successfully to {} for customer {} ({} format)",
          recipientEmail,
          customerId,
          statementFormat);

    } catch (Exception e) {
      logger.error(
          "Failed to send AR statement email to {} for customer {}: {}",
          recipientEmail,
          customerId,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to send AR statement email", e);
    }
  }
}
