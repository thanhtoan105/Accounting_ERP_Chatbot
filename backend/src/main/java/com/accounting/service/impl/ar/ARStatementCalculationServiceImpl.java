package com.accounting.service.impl.ar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARStatementDetailedDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import com.accounting.entity.ARPayment;
import com.accounting.entity.ReceiptAllocation;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARStatementCalculationService;

/**
 * Implementation of ARStatementCalculationService for generating summary and detailed customer statements.
 */
@Service
@Transactional(readOnly = true)
public class ARStatementCalculationServiceImpl implements ARStatementCalculationService {

  private static final Logger logger =
      LoggerFactory.getLogger(ARStatementCalculationServiceImpl.class);

  private final SalesInvoiceRepository invoiceRepository;
  private final ReceiptAllocationRepository receiptAllocationRepository;
  private final ARPaymentRepository paymentRepository;
  private final CustomerRepository customerRepository;

  public ARStatementCalculationServiceImpl(
      SalesInvoiceRepository invoiceRepository,
      ReceiptAllocationRepository receiptAllocationRepository,
      ARPaymentRepository paymentRepository,
      CustomerRepository customerRepository) {
    this.invoiceRepository = invoiceRepository;
    this.receiptAllocationRepository = receiptAllocationRepository;
    this.paymentRepository = paymentRepository;
    this.customerRepository = customerRepository;
  }

  @Override
  public ARStatementSummaryDTO generateSummaryStatement(Long customerId, LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }

    logger.debug("Generating summary statement for customerId={}, asOfDate={}", customerId, asOfDate);

    // Get customer
    var customer =
        customerRepository
            .findByCompanyIdAndId(companyId, customerId)
            .orElseThrow(() -> {
              logger.warn("Customer not found for summary statement: customerId={}, companyId={}", customerId, companyId);
              return new IllegalArgumentException("Customer not found: " + customerId);
            });

    // Get POSTED invoices for customer (up to asOfDate)
    // Use customer-specific query to avoid scanning all invoices
    List<SalesInvoice> invoices =
        invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceDateLessThanEqual(
            companyId, customerId, asOfDate);

    // Filter to customer and POSTED status, exclude REVERSED and fully PAID
    invoices =
        invoices.stream()
            .filter(
                inv ->
                    inv.getCustomerId().equals(customerId)
                        && (inv.getStatus() == SalesInvoiceStatus.POSTED
                            || inv.getStatus() == SalesInvoiceStatus.PARTIALLY_PAID)
                        && inv.getRemainingBalance().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(SalesInvoice::getInvoiceDate))
            .collect(Collectors.toList());

    // Build summary statement
    ARStatementSummaryDTO statement = new ARStatementSummaryDTO();
    statement.setId(UUID.randomUUID());
    statement.setCustomerId(customerId);
    statement.setCustomerName(customer.getName());
    statement.setCustomerCode(customer.getCode());
    statement.setCustomerAddress(customer.getAddress());
    statement.setCustomerTaxCode(customer.getTaxCode());
    statement.setFormat(com.accounting.entity.ARStatementHistory.StatementFormat.SUMMARY);
    statement.setAsOfDate(asOfDate);

    // Calculate totals and build invoice lines
    BigDecimal totalInvoices = BigDecimal.ZERO;
    BigDecimal totalPaid = BigDecimal.ZERO;
    BigDecimal runningBalance = BigDecimal.ZERO;
    List<ARStatementSummaryDTO.StatementInvoiceDTO> invoiceLines = new ArrayList<>();

    for (SalesInvoice invoice : invoices) {
      ARStatementSummaryDTO.StatementInvoiceDTO line =
          new ARStatementSummaryDTO.StatementInvoiceDTO();
      line.setInvoiceId(invoice.getId());
      line.setInvoiceNumber(invoice.getInvoiceNumber());
      line.setInvoiceDate(invoice.getInvoiceDate());
      line.setInvoiceAmount(invoice.getTotalAmount());
      line.setAmountPaid(invoice.getAmountPaid());
      line.setBalance(invoice.getRemainingBalance());
      runningBalance = runningBalance.add(invoice.getRemainingBalance());
      line.setRunningBalance(runningBalance);

      totalInvoices = totalInvoices.add(invoice.getTotalAmount());
      totalPaid = totalPaid.add(invoice.getAmountPaid());
      invoiceLines.add(line);
    }

    statement.setInvoices(invoiceLines);
    statement.setTotalInvoices(totalInvoices);
    statement.setTotalPaid(totalPaid);
    statement.setTotalOutstanding(runningBalance);

    return statement;
  }

  @Override
  public ARStatementDetailedDTO generateDetailedStatement(Long customerId, LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }

    // Get customer
    var customer =
        customerRepository
            .findByCompanyIdAndId(companyId, customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

    // Get POSTED invoices for customer (up to asOfDate)
    // Use customer-specific query to avoid scanning all invoices
    List<SalesInvoice> invoices =
        invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceDateLessThanEqual(
            companyId, customerId, asOfDate);

    invoices =
        invoices.stream()
            .filter(
                inv ->
                    inv.getCustomerId().equals(customerId)
                        && (inv.getStatus() == SalesInvoiceStatus.POSTED
                            || inv.getStatus() == SalesInvoiceStatus.PARTIALLY_PAID))
            .sorted(Comparator.comparing(SalesInvoice::getInvoiceDate))
            .collect(Collectors.toList());

    // Build detailed statement
    ARStatementDetailedDTO statement = new ARStatementDetailedDTO();
    statement.setId(UUID.randomUUID());
    statement.setCustomerId(customerId);
    statement.setCustomerName(customer.getName());
    statement.setCustomerCode(customer.getCode());
    statement.setCustomerAddress(customer.getAddress());
    statement.setCustomerTaxCode(customer.getTaxCode());
    statement.setFormat(com.accounting.entity.ARStatementHistory.StatementFormat.DETAILED);
    statement.setAsOfDate(asOfDate);

    // Build transaction-level lines with receipts
    List<ARStatementDetailedDTO.StatementTransactionDTO> transactions = new ArrayList<>();
    BigDecimal runningBalance = BigDecimal.ZERO;
    BigDecimal totalInvoices = BigDecimal.ZERO;
    BigDecimal totalPaid = BigDecimal.ZERO;

    for (SalesInvoice invoice : invoices) {
      // Add invoice transaction
      ARStatementDetailedDTO.StatementTransactionDTO invoiceTx =
          new ARStatementDetailedDTO.StatementTransactionDTO();
      invoiceTx.setType("INVOICE");
      invoiceTx.setInvoiceId(invoice.getId());
      invoiceTx.setInvoiceNumber(invoice.getInvoiceNumber());
      invoiceTx.setTransactionDate(invoice.getInvoiceDate());
      invoiceTx.setReference(invoice.getReference());
      invoiceTx.setDescription(invoice.getDescription());
      invoiceTx.setDebit(invoice.getTotalAmount());
      invoiceTx.setCredit(BigDecimal.ZERO);
      runningBalance = runningBalance.add(invoice.getTotalAmount());
      invoiceTx.setRunningBalance(runningBalance);
      transactions.add(invoiceTx);
      totalInvoices = totalInvoices.add(invoice.getTotalAmount());

      // Get receipt allocations for this invoice
      List<ReceiptAllocation> allocations =
          receiptAllocationRepository.findBySalesInvoiceId(invoice.getId());

      // Get payments for these allocations
      for (ReceiptAllocation allocation : allocations) {
        var paymentOpt = paymentRepository.findById(allocation.getReceiptId());
        if (paymentOpt.isPresent()) {
          ARPayment payment = paymentOpt.get();
          if (payment.getStatus() == com.accounting.entity.ReceiptStatus.POSTED
              && payment.getReceiptDate().isBefore(asOfDate.plusDays(1))) {
            // Add receipt transaction
            ARStatementDetailedDTO.StatementTransactionDTO receiptTx =
                new ARStatementDetailedDTO.StatementTransactionDTO();
            receiptTx.setType("RECEIPT");
            receiptTx.setInvoiceId(invoice.getId());
            receiptTx.setInvoiceNumber(invoice.getInvoiceNumber());
            receiptTx.setReceiptId(payment.getId());
            receiptTx.setReceiptNumber(payment.getReceiptNumber());
            receiptTx.setTransactionDate(payment.getReceiptDate());
            receiptTx.setReference(payment.getReference());
            receiptTx.setDescription("Payment: " + payment.getReceiptNumber());
            receiptTx.setDebit(BigDecimal.ZERO);
            receiptTx.setCredit(allocation.getAllocatedAmount());
            runningBalance = runningBalance.subtract(allocation.getAllocatedAmount());
            receiptTx.setRunningBalance(runningBalance);
            transactions.add(receiptTx);
            totalPaid = totalPaid.add(allocation.getAllocatedAmount());
          }
        }
      }
    }

    // Sort transactions by date
    transactions.sort(
        Comparator.comparing(ARStatementDetailedDTO.StatementTransactionDTO::getTransactionDate)
            .thenComparing(tx -> tx.getType().equals("INVOICE") ? 0 : 1));

    statement.setTransactions(transactions);
    statement.setTotalInvoices(totalInvoices);
    statement.setTotalPaid(totalPaid);
    statement.setTotalOutstanding(runningBalance);

    logger.debug("Detailed statement generated: {} transactions, total outstanding: {}", 
        transactions.size(), runningBalance);

    return statement;
  }
}
