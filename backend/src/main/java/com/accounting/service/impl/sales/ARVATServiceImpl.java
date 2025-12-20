package com.accounting.service.impl.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.VATValidationResultDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.VatRate;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARVATService;
import com.accounting.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of ARVATService for AR (Accounts Receivable) VAT validation,
 * calculation, and GL split generation.
 * Handles output VAT for sales invoices with revenue accounts (5xx) and output
 * VAT account (33311).
 */
@Service
@Transactional(readOnly = true)
public class ARVATServiceImpl implements ARVATService {

  private static final Logger logger = LoggerFactory.getLogger(ARVATServiceImpl.class);
  private static final BigDecimal VAT_SUM_TOLERANCE = new BigDecimal("1000.00"); // 1,000₫
  private static final BigDecimal ROUNDING_UNIT = new BigDecimal("100.00"); // Round to nearest 100 VND
  private static final String AR_ACCOUNT_CODE = "131"; // Accounts Receivable
  private static final String OUTPUT_VAT_ACCOUNT_CODE = "33311"; // Output VAT (Thuế GTGT đầu ra)
  private static final String REVENUE_ACCOUNT_PREFIX = "5"; // Revenue accounts start with 5

  // Default VAT rate for company
  // TODO: Read from CompanySettings.defaultVatRate field when available
  private static final VatRate DEFAULT_VAT_RATE = VatRate.TEN;

  private final SalesInvoiceLineRepository salesInvoiceLineRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final AuditService auditService;

  @Autowired
  public ARVATServiceImpl(
      SalesInvoiceLineRepository salesInvoiceLineRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      AuditService auditService) {
    this.salesInvoiceLineRepository = salesInvoiceLineRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.auditService = auditService;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public VATValidationResultDTO validateVATRate(VatRate rate, Long companyId) {
    VATValidationResultDTO result = new VATValidationResultDTO(true);

    // Validate rate is one of: 0, 5, 10, EXEMPT
    if (rate == null) {
      result.addError("VAT rate is required");
      return result;
    }

    boolean isValidRate = rate == VatRate.ZERO
        || rate == VatRate.FIVE
        || rate == VatRate.TEN
        || rate == VatRate.EXEMPT;

    if (!isValidRate) {
      result.addError("Invalid VAT rate. Must be one of: 0%, 5%, 10%, or EXEMPT");
      return result;
    }

    result.setValidatedRate(rate);

    // Check against company default VAT rate (if different, add warning)
    // TODO: Read from CompanySettings.defaultVatRate field when available
    VatRate companyDefault = DEFAULT_VAT_RATE;
    if (rate != companyDefault) {
      result.addWarning(
          String.format(
              "You are overriding the default VAT rate from %s to %s. Ensure this is correct per customer agreement.",
              companyDefault.getDisplayName(), rate.getDisplayName()));

      // Log override to audit
      logVATRateOverride(companyId, rate, companyDefault);
    }

    return result;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public BigDecimal calculateVAT(BigDecimal lineTotal, VatRate vatRate) {
    if (lineTotal == null || lineTotal.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    if (vatRate == null || vatRate == VatRate.EXEMPT || vatRate == VatRate.ZERO) {
      return BigDecimal.ZERO;
    }

    // Calculate VAT: lineTotal × (vatRate / 100)
    BigDecimal vatAmount = lineTotal.multiply(vatRate.getRate());

    // Round to nearest 100 VND per Circular 200
    // Divide by 100, round to nearest integer, multiply by 100
    BigDecimal rounded = vatAmount
        .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
        .multiply(ROUNDING_UNIT);

    return rounded.setScale(2, RoundingMode.HALF_UP);
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public VATValidationResultDTO validateVATSum(SalesInvoice invoice) {
    VATValidationResultDTO result = new VATValidationResultDTO(true);

    if (invoice == null) {
      result.addError("Sales invoice is required");
      return result;
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      result.addError("Missing company context");
      return result;
    }

    // Get all line items for the invoice
    List<SalesInvoiceLine> lines = salesInvoiceLineRepository
        .findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());

    // Calculate sum of line-level VAT amounts (after rounding)
    BigDecimal lineVATSum = lines.stream()
        .map(SalesInvoiceLine::getVatAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Get document-level VAT total
    BigDecimal documentVAT = invoice.getVatAmount() != null ? invoice.getVatAmount() : BigDecimal.ZERO;

    result.setCalculatedVATAmount(lineVATSum);
    result.setDocumentVATAmount(documentVAT);

    // Calculate difference
    BigDecimal difference = lineVATSum.subtract(documentVAT).abs();
    result.setDifference(difference);

    // Validate: mismatch ≥ 1000 VND blocks post
    if (difference.compareTo(VAT_SUM_TOLERANCE) >= 0) {
      result.addError(
          String.format(
              "VAT rounding variance: %s VND. Line-level VAT sum (%s) differs from document VAT (%s) by %s, exceeding tolerance of %s. Posting is blocked.",
              difference, lineVATSum, documentVAT, difference, VAT_SUM_TOLERANCE));

      // Log validation failure to audit
      logVATSumValidationFailure(companyId, invoice.getId(), lineVATSum, documentVAT, difference);
    } else if (difference.compareTo(BigDecimal.ZERO) > 0) {
      // Within tolerance but not exact - add warning
      result.addWarning(
          String.format(
              "VAT rounding variance: %s VND. Line-level VAT sum (%s) differs from document VAT (%s) by %s (within tolerance).",
              difference, lineVATSum, documentVAT, difference));
    }

    return result;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public List<VoucherEntryLineRequest> generateGLSplit(SalesInvoice invoice) {
    if (invoice == null) {
      throw new IllegalArgumentException("Sales invoice is required");
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Get AR account (131)
    ChartOfAccount arAccount = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, AR_ACCOUNT_CODE)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "AR account (131) not found in chart of accounts"));

    // Get Output VAT account (33311)
    ChartOfAccount vatAccount = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, OUTPUT_VAT_ACCOUNT_CODE)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Output VAT account (33311) not found in chart of accounts"));

    // Validate accounts are postable and leaf accounts
    validateAccount(arAccount, "AR account (131)");
    validateAccount(vatAccount, "Output VAT account (33311)");

    // Get all line items for the invoice
    List<SalesInvoiceLine> lines = salesInvoiceLineRepository
        .findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    // For each line, create: Dr 131 (AR), Cr {revenueAccount} (revenue), Cr 33311
    // (output VAT)
    for (SalesInvoiceLine line : lines) {
      // Get revenue account for this line
      ChartOfAccount revenueAccount = chartOfAccountsRepository
          .findByIdAndCompanyId(line.getAccountId(), companyId)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND,
              "Revenue account not found: " + line.getAccountId()));

      // Validate revenue account is in 5xx range and is leaf/postable
      validateRevenueAccount(revenueAccount);

      BigDecimal lineTotal = line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO;
      BigDecimal vatAmount = line.getVatAmount() != null ? line.getVatAmount() : BigDecimal.ZERO;
      BigDecimal revenueAmount = lineTotal; // Revenue is the line total (before VAT)

      // Entry 1: Dr 131, Cr {revenueAccount}
      VoucherEntryLineRequest entry1 = new VoucherEntryLineRequest();
      entry1.setDebitAccountId(arAccount.getId());
      entry1.setCreditAccountId(revenueAccount.getId());
      entry1.setAmount(revenueAmount);
      entry1.setDescription(
          String.format("Invoice %s - Line %d: %s", invoice.getInvoiceNumber(), line.getLineNumber(),
              line.getDescription()));
      entry1.setCustomerId(invoice.getCustomerId());
      entryLines.add(entry1);

      // Entry 2: Dr 131, Cr 33311 (if VAT > 0)
      if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
        VoucherEntryLineRequest entry2 = new VoucherEntryLineRequest();
        entry2.setDebitAccountId(arAccount.getId());
        entry2.setCreditAccountId(vatAccount.getId());
        entry2.setAmount(vatAmount);
        entry2.setDescription(
            String.format("Invoice %s - Line %d: Output VAT %s%%", invoice.getInvoiceNumber(), line.getLineNumber(),
                line.getVatRate() != null ? line.getVatRate().getDisplayName() : "0%"));
        entry2.setCustomerId(invoice.getCustomerId());
        entryLines.add(entry2);
      }
    }

    // Validate balance: total debit = total credit
    BigDecimal totalDebit = entryLines.stream()
        .map(VoucherEntryLineRequest::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalCredit = entryLines.stream()
        .map(VoucherEntryLineRequest::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalDebit.compareTo(totalCredit) != 0) {
      throw new IllegalStateException(
          String.format("GL split imbalance: Debit %s ≠ Credit %s", totalDebit, totalCredit));
    }

    logger.debug("Generated {} GL split entries for invoice {}", entryLines.size(), invoice.getId());
    return entryLines;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public List<VoucherEntryLineRequest> generateCreditNoteGLSplit(
      SalesInvoice creditNote, SalesInvoice originalInvoice) {
    if (creditNote == null || originalInvoice == null) {
      throw new IllegalArgumentException("Credit note and original invoice are required");
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Generate normal GL split first
    List<VoucherEntryLineRequest> normalEntries = generateGLSplit(creditNote);

    // Invert all entries: swap debit/credit accounts
    List<VoucherEntryLineRequest> invertedEntries = new ArrayList<>();
    for (VoucherEntryLineRequest entry : normalEntries) {
      VoucherEntryLineRequest inverted = new VoucherEntryLineRequest();
      // Swap: original debit becomes credit, original credit becomes debit
      inverted.setDebitAccountId(entry.getCreditAccountId());
      inverted.setCreditAccountId(entry.getDebitAccountId());
      inverted.setAmount(entry.getAmount());
      inverted.setDescription(
          "Credit Note: " + (entry.getDescription() != null ? entry.getDescription() : ""));
      inverted.setCustomerId(creditNote.getCustomerId());
      invertedEntries.add(inverted);
    }

    logger.debug("Generated {} inverted GL split entries for credit note {}", invertedEntries.size(),
        creditNote.getId());
    return invertedEntries;
  }

  /**
   * Validate account is postable and is a leaf account (no children).
   */
  private void validateAccount(ChartOfAccount account, String accountName) {
    if (account == null) {
      throw new IllegalArgumentException(accountName + " is required");
    }

    if (!Boolean.TRUE.equals(account.getPostable())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, accountName + " is not postable");
    }

    if (chartOfAccountsRepository.hasChildren(account.getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, accountName + " is not a leaf account (has children)");
    }
  }

  /**
   * Validate revenue account is in 5xx range and is leaf/postable.
   */
  private void validateRevenueAccount(ChartOfAccount account) {
    validateAccount(account, "Revenue account");

    String code = account.getCode();
    if (code == null || !code.startsWith(REVENUE_ACCOUNT_PREFIX)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Revenue account code must start with '5' (5xx range). Found: " + code);
    }
  }

  /**
   * Log VAT rate override to audit trail.
   */
  private void logVATRateOverride(Long companyId, VatRate actualRate, VatRate defaultRate) {
    try {
      HttpServletRequest request = getCurrentRequest();
      Long userId = null;
      try {
        userId = com.accounting.security.SecurityUtils.getCurrentUserId();
      } catch (Exception e) {
        logger.debug("Unable to get current user ID for VAT rate override logging", e);
      }
      auditService.logVatRateOverride(
          companyId,
          userId,
          actualRate != null ? actualRate.getDisplayName() : null,
          defaultRate != null ? defaultRate.getDisplayName() : null,
          request);
    } catch (Exception e) {
      logger.error("Failed to log VAT rate override", e);
    }
  }

  /**
   * Log VAT sum validation failure to audit trail.
   */
  private void logVATSumValidationFailure(
      Long companyId,
      UUID invoiceId,
      BigDecimal lineVATSum,
      BigDecimal documentVAT,
      BigDecimal difference) {
    try {
      HttpServletRequest request = getCurrentRequest();
      Long userId = null;
      try {
        userId = com.accounting.security.SecurityUtils.getCurrentUserId();
      } catch (Exception e) {
        logger.debug("Unable to get current user ID for VAT sum validation failure logging", e);
      }
      auditService.logVatSumValidationFailure(
          companyId,
          userId,
          invoiceId,
          lineVATSum,
          documentVAT,
          difference,
          request);
    } catch (Exception e) {
      logger.error("Failed to log VAT sum validation failure", e);
    }
  }

  /**
   * Get current HTTP request from context.
   */
  private HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attributes != null ? attributes.getRequest() : null;
    } catch (Exception e) {
      logger.debug("Could not get current request from context", e);
      return null;
    }
  }
}
