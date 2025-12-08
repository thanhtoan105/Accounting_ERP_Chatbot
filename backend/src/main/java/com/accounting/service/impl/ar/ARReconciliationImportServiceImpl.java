package com.accounting.service.impl.ar;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ARReconciliationImportDTO;
import com.accounting.entity.ARStatementDispute;
import com.accounting.entity.SalesInvoice;
import com.accounting.repository.ARStatementDisputeRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;

/**
 * Implementation of ARReconciliationImportService for CSV import and mismatch
 * detection.
 */
@Service
@Transactional(readOnly = true)
public class ARReconciliationImportServiceImpl
    implements com.accounting.service.ARReconciliationImportService {

  private static final Logger logger = LoggerFactory.getLogger(ARReconciliationImportServiceImpl.class);
  private static final BigDecimal VARIANCE_THRESHOLD = new BigDecimal("1000.00"); // 1000 VND

  private final SalesInvoiceRepository invoiceRepository;
  private final ARStatementDisputeRepository disputeRepository;

  public ARReconciliationImportServiceImpl(
      SalesInvoiceRepository invoiceRepository,
      ARStatementDisputeRepository disputeRepository) {
    this.invoiceRepository = invoiceRepository;
    this.disputeRepository = disputeRepository;
  }

  @Override
  @Transactional
  public ARReconciliationImportDTO importReconciliation(Long customerId, MultipartFile file) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    UUID reconciliationId = UUID.randomUUID();
    ARReconciliationImportDTO result = new ARReconciliationImportDTO();
    result.setReconciliationId(reconciliationId);
    result.setCustomerId(customerId);
    result.setMatchedCount(0);
    result.setMismatchCount(0);

    List<ARReconciliationImportDTO.ReconciliationMismatchDTO> mismatches = new ArrayList<>();

    try {
      // Parse CSV file using Apache Commons CSV (handles quoted fields properly)
      CSVParser parser = CSVFormat.Builder.create()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setIgnoreHeaderCase(true)
          .setTrim(true)
          .build()
          .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

      // Get header map for column lookup
      var headerMap = parser.getHeaderMap();
      if (headerMap == null || headerMap.isEmpty()) {
        throw new IllegalArgumentException("CSV file is empty or has no headers");
      }

      // Find column indices by flexible header matching
      String invoiceNumberHeader = null;
      String customerAmountHeader = null;
      String customerPaymentHeader = null;
      String notesHeader = null;

      for (String header : headerMap.keySet()) {
        String lowerHeader = header.toLowerCase();
        if (invoiceNumberHeader == null
            && lowerHeader.contains("invoice")
            && lowerHeader.contains("number")) {
          invoiceNumberHeader = header;
        } else if (customerAmountHeader == null
            && lowerHeader.contains("customer")
            && lowerHeader.contains("amount")) {
          customerAmountHeader = header;
        } else if (customerPaymentHeader == null
            && lowerHeader.contains("customer")
            && lowerHeader.contains("payment")) {
          customerPaymentHeader = header;
        } else if (notesHeader == null && lowerHeader.contains("notes")) {
          notesHeader = header;
        }
      }

      if (invoiceNumberHeader == null || customerAmountHeader == null) {
        throw new IllegalArgumentException(
            "CSV must contain InvoiceNumber and CustomerAmount columns");
      }

      // Process rows
      int matchedCount = 0;
      for (CSVRecord record : parser) {
        if (record.size() < headerMap.size()) {
          logger.warn("Skipping row with insufficient columns: {}", record);
          continue; // Skip invalid rows
        }

        String invoiceNumber = record.get(invoiceNumberHeader).trim();
        if (invoiceNumber.isEmpty()) {
          continue; // Skip rows with empty invoice number
        }

        BigDecimal customerAmount = parseBigDecimal(record.get(customerAmountHeader).trim());
        // Customer payment is parsed but not currently used in mismatch detection
        // Reserved for future reconciliation features
        @SuppressWarnings("unused")
        BigDecimal customerPayment = customerPaymentHeader != null && record.isSet(customerPaymentHeader)
            ? parseBigDecimal(record.get(customerPaymentHeader).trim())
            : BigDecimal.ZERO;
        String notes = notesHeader != null && record.isSet(notesHeader)
            ? record.get(notesHeader).trim()
            : "";

        // Find invoice by number (optimized query)
        var invoiceOpt = invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceNumberIgnoreCase(
            companyId, customerId, invoiceNumber);
        SalesInvoice invoice = invoiceOpt.orElse(null);

        if (invoice != null) {
          matchedCount++;
          BigDecimal systemAmount = invoice.getTotalAmount();
          BigDecimal variance = systemAmount.subtract(customerAmount).abs();

          // Check for mismatch
          if (variance.compareTo(BigDecimal.ZERO) > 0) {
            ARReconciliationImportDTO.ReconciliationMismatchDTO mismatch = new ARReconciliationImportDTO.ReconciliationMismatchDTO();
            mismatch.setInvoiceId(invoice.getId());
            mismatch.setInvoiceNumber(invoiceNumber);
            mismatch.setSystemAmount(systemAmount);
            mismatch.setCustomerAmount(customerAmount);
            mismatch.setVariance(variance);
            mismatch.setVarianceType(
                variance.compareTo(VARIANCE_THRESHOLD) > 0
                    ? "SIGNIFICANT"
                    : "ROUNDING");
            mismatch.setNotes(notes);
            mismatches.add(mismatch);

            // Create dispute log entry
            ARStatementDispute dispute = new ARStatementDispute();
            dispute.setCompanyId(companyId);
            dispute.setReconciliationId(reconciliationId);
            dispute.setInvoiceId(invoice.getId());
            dispute.setInvoiceNumber(invoiceNumber);
            dispute.setSystemAmount(systemAmount);
            dispute.setCustomerAmount(customerAmount);
            dispute.setVariance(variance);
            dispute.setVarianceTypeEnum(
                variance.compareTo(VARIANCE_THRESHOLD) > 0
                    ? ARStatementDispute.VarianceType.SIGNIFICANT
                    : ARStatementDispute.VarianceType.ROUNDING);
            dispute.setNotes(notes);
            dispute.setStatusEnum(ARStatementDispute.DisputeStatus.OPEN);
            disputeRepository.save(dispute);
          }
        }
      }

      parser.close();

      result.setMatchedCount(matchedCount);
      result.setMismatchCount(mismatches.size());
      result.setMismatches(mismatches);

    } catch (Exception e) {
      logger.error("Failed to import reconciliation CSV", e);
      throw new RuntimeException("Failed to import reconciliation: " + e.getMessage(), e);
    }

    return result;
  }

  private BigDecimal parseBigDecimal(String value) {
    if (value == null || value.trim().isEmpty()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(value.trim().replaceAll("[^0-9.-]", ""));
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }
}
