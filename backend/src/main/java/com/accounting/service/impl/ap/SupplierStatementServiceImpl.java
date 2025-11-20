package com.accounting.service.impl.ap;

import com.accounting.dto.DetailedStatementDTO;
import com.accounting.dto.ReconciliationResultDTO;
import com.accounting.dto.SupplierStatementDTO;
import com.accounting.dto.SupplierStatementDisputeDTO;
import com.accounting.dto.SupplierStatementHistoryDTO;
import com.accounting.dto.UpdateDisputeRequest;
import com.accounting.entity.*;
import com.accounting.repository.*;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.SupplierStatementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of SupplierStatementService for supplier statement generation, reconciliation, and
 * dispute management.
 */
@Service
@Transactional(readOnly = true)
public class SupplierStatementServiceImpl implements SupplierStatementService {

  private static final Logger logger = LoggerFactory.getLogger(SupplierStatementServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final BigDecimal TOLERANCE = new BigDecimal("1000.00"); // ±1,000₫ tolerance

  private final PurchaseBillRepository billRepository;
  private final APPaymentRepository paymentRepository;
  private final PaymentAllocationRepository allocationRepository;
  private final SupplierRepository supplierRepository;
  private final UserRepository userRepository;
  private final SupplierStatementHistoryRepository historyRepository;
  private final SupplierStatementDisputeRepository disputeRepository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public SupplierStatementServiceImpl(
      PurchaseBillRepository billRepository,
      APPaymentRepository paymentRepository,
      PaymentAllocationRepository allocationRepository,
      SupplierRepository supplierRepository,
      UserRepository userRepository,
      SupplierStatementHistoryRepository historyRepository,
      SupplierStatementDisputeRepository disputeRepository,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.billRepository = billRepository;
    this.paymentRepository = paymentRepository;
    this.allocationRepository = allocationRepository;
    this.supplierRepository = supplierRepository;
    this.userRepository = userRepository;
    this.historyRepository = historyRepository;
    this.disputeRepository = disputeRepository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public SupplierStatementDTO generateSummaryStatement(
      Long supplierId, LocalDate startDate, LocalDate endDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Get supplier
    Supplier supplier =
        supplierRepository
            .findByCompanyIdAndId(companyId, supplierId)
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));

    // Get bills in date range (POSTED, PAID, PARTIALLY_PAID)
    List<PurchaseBill> bills = getPostedBillsForSupplier(companyId, supplierId, startDate, endDate);

    // Calculate opening balance (bills before start date with remaining balance)
    BigDecimal openingBalance = calculateOpeningBalance(companyId, supplierId, startDate);

    // Generate statement DTO
    SupplierStatementDTO statement = new SupplierStatementDTO();
    statement.setId(UUID.randomUUID());
    statement.setSupplierId(supplierId);
    statement.setSupplierName(supplier.getName());
    statement.setSupplierCode(supplier.getCode());
    statement.setStatementType(SupplierStatementHistory.StatementType.SUMMARY);
    statement.setStartDate(startDate);
    statement.setEndDate(endDate);
    statement.setGenerationDate(Instant.now());
    statement.setGeneratedByName(getCurrentUsername());
    statement.setOpeningBalance(openingBalance);

    // Build statement lines
    List<SupplierStatementDTO.StatementLineItemDTO> items = new ArrayList<>();
    BigDecimal runningBalance = openingBalance;
    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;

    // Add bills and payments in chronological order
    for (PurchaseBill bill : bills) {
      // Add bill line
      SupplierStatementDTO.StatementLineItemDTO billLine =
          new SupplierStatementDTO.StatementLineItemDTO();
      billLine.setType("BILL");
      billLine.setDate(bill.getBillDate());
      billLine.setReference(bill.getReference());
      billLine.setDescription(bill.getDescription());
      billLine.setDebit(bill.getTotalAmount());
      billLine.setCredit(BigDecimal.ZERO);
      billLine.setBillId(bill.getId());
      billLine.setBillNumber(bill.getBillNumber());
      runningBalance = runningBalance.add(bill.getTotalAmount());
      billLine.setBalance(runningBalance);
      totalDebits = totalDebits.add(bill.getTotalAmount());
      items.add(billLine);

      // Add payment lines for this bill
      List<PaymentAllocation> allocations =
          allocationRepository.findByPurchaseBillId(bill.getId());
      for (PaymentAllocation allocation : allocations) {
        APPayment payment = allocation.getPayment();
        if (payment != null
            && payment.getPaymentDate().compareTo(startDate) >= 0
            && payment.getPaymentDate().compareTo(endDate) <= 0) {
          SupplierStatementDTO.StatementLineItemDTO paymentLine =
              new SupplierStatementDTO.StatementLineItemDTO();
          paymentLine.setType("PAYMENT");
          paymentLine.setDate(payment.getPaymentDate());
          paymentLine.setReference(payment.getReference());
          paymentLine.setDescription("Payment: " + payment.getPaymentNumber());
          paymentLine.setDebit(BigDecimal.ZERO);
          paymentLine.setCredit(allocation.getAllocatedAmount());
          paymentLine.setPaymentId(payment.getId());
          paymentLine.setPaymentNumber(payment.getPaymentNumber());
          runningBalance = runningBalance.subtract(allocation.getAllocatedAmount());
          paymentLine.setBalance(runningBalance);
          totalCredits = totalCredits.add(allocation.getAllocatedAmount());
          items.add(paymentLine);
        }
      }
    }

    // Sort by date
    items.sort(Comparator.comparing(SupplierStatementDTO.StatementLineItemDTO::getDate));

    statement.setItems(items);
    statement.setTotalDebits(totalDebits);
    statement.setTotalCredits(totalCredits);
    statement.setClosingBalance(runningBalance);

    // Save statement history
    SupplierStatementHistory history = createStatementHistory(
        companyId,
        supplierId,
        SupplierStatementHistory.StatementType.SUMMARY,
        startDate,
        endDate,
        statement);
    historyRepository.save(history);
    
    // Update statement DTO with persisted ID
    statement.setId(history.getId());

    // Log audit event
    try {
        auditService.logStatementGenerated(
            companyId,
            getCurrentUserId(),
            history.getId(),
            supplierId,
            SupplierStatementHistory.StatementType.SUMMARY.name(),
            null); // No specific request object available/needed here
    } catch (Exception e) {
        logger.error("Failed to log statement generation audit event", e);
    }

    return statement;
  }

  @Override
  @Transactional
  public DetailedStatementDTO generateDetailedStatement(
      Long supplierId, LocalDate startDate, LocalDate endDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Get supplier
    Supplier supplier =
        supplierRepository
            .findByCompanyIdAndId(companyId, supplierId)
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));

    // Get bills in date range (POSTED, PAID, PARTIALLY_PAID)
    List<PurchaseBill> bills = getPostedBillsForSupplier(companyId, supplierId, startDate, endDate);

    // Calculate opening balance (bills before start date with remaining balance)
    BigDecimal openingBalance = calculateOpeningBalance(companyId, supplierId, startDate);

    // Generate base summary statement DTO (without saving history yet)
    SupplierStatementDTO summaryStatement = new SupplierStatementDTO();
    summaryStatement.setSupplierId(supplierId);
    summaryStatement.setSupplierName(supplier.getName());
    summaryStatement.setSupplierCode(supplier.getCode());
    summaryStatement.setStatementType(SupplierStatementHistory.StatementType.DETAILED);
    summaryStatement.setStartDate(startDate);
    summaryStatement.setEndDate(endDate);
    summaryStatement.setGenerationDate(Instant.now());
    summaryStatement.setGeneratedByName(getCurrentUsername());
    summaryStatement.setOpeningBalance(openingBalance);

    // Build statement lines
    List<SupplierStatementDTO.StatementLineItemDTO> items = new ArrayList<>();
    BigDecimal runningBalance = openingBalance;
    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;

    // Add bills and payments in chronological order
    for (PurchaseBill bill : bills) {
      // Add bill line
      SupplierStatementDTO.StatementLineItemDTO billLine =
          new SupplierStatementDTO.StatementLineItemDTO();
      billLine.setType("BILL");
      billLine.setDate(bill.getBillDate());
      billLine.setReference(bill.getReference());
      billLine.setDescription(bill.getDescription());
      billLine.setDebit(bill.getTotalAmount());
      billLine.setCredit(BigDecimal.ZERO);
      billLine.setBillId(bill.getId());
      billLine.setBillNumber(bill.getBillNumber());
      runningBalance = runningBalance.add(bill.getTotalAmount());
      billLine.setBalance(runningBalance);
      totalDebits = totalDebits.add(bill.getTotalAmount());
      items.add(billLine);

      // Add payment lines for this bill
      List<PaymentAllocation> allocations =
          allocationRepository.findByPurchaseBillId(bill.getId());
      for (PaymentAllocation allocation : allocations) {
        APPayment payment = allocation.getPayment();
        if (payment != null
            && payment.getPaymentDate().compareTo(startDate) >= 0
            && payment.getPaymentDate().compareTo(endDate) <= 0) {
          SupplierStatementDTO.StatementLineItemDTO paymentLine =
              new SupplierStatementDTO.StatementLineItemDTO();
          paymentLine.setType("PAYMENT");
          paymentLine.setDate(payment.getPaymentDate());
          paymentLine.setReference(payment.getReference());
          paymentLine.setDescription("Payment: " + payment.getPaymentNumber());
          paymentLine.setDebit(BigDecimal.ZERO);
          paymentLine.setCredit(allocation.getAllocatedAmount());
          paymentLine.setPaymentId(payment.getId());
          paymentLine.setPaymentNumber(payment.getPaymentNumber());
          runningBalance = runningBalance.subtract(allocation.getAllocatedAmount());
          paymentLine.setBalance(runningBalance);
          totalCredits = totalCredits.add(allocation.getAllocatedAmount());
          items.add(paymentLine);
        }
      }
    }

    // Sort by date
    items.sort(Comparator.comparing(SupplierStatementDTO.StatementLineItemDTO::getDate));

    summaryStatement.setItems(items);
    summaryStatement.setTotalDebits(totalDebits);
    summaryStatement.setTotalCredits(totalCredits);
    summaryStatement.setClosingBalance(runningBalance);

    // Convert to detailed statement
    DetailedStatementDTO detailedStatement = new DetailedStatementDTO();
    copyBasicStatementFields(summaryStatement, detailedStatement);

    // Get detailed bill information
    List<DetailedStatementDTO.DetailedBillItemDTO> billDetails = new ArrayList<>();
    for (PurchaseBill bill : bills) {
      DetailedStatementDTO.DetailedBillItemDTO billDetail =
          new DetailedStatementDTO.DetailedBillItemDTO();
      billDetail.setBillId(bill.getId());
      billDetail.setBillNumber(bill.getBillNumber());
      billDetail.setBillDate(bill.getBillDate());
      billDetail.setDueDate(bill.getDueDate());
      billDetail.setReference(bill.getReference());
      billDetail.setDescription(bill.getDescription());
      billDetail.setTotalAmount(bill.getTotalAmount());
      billDetail.setRemainingBalance(calculateRemainingBalance(bill.getId()));

      // Add payment events
      List<PaymentAllocation> allocations = allocationRepository.findByPurchaseBillId(bill.getId());
      List<DetailedStatementDTO.PaymentEventDTO> paymentEvents = new ArrayList<>();
      for (PaymentAllocation allocation : allocations) {
        APPayment payment = allocation.getPayment();
        if (payment != null) {
          DetailedStatementDTO.PaymentEventDTO event =
              new DetailedStatementDTO.PaymentEventDTO();
          event.setPaymentId(payment.getId());
          event.setPaymentNumber(payment.getPaymentNumber());
          event.setPaymentDate(payment.getPaymentDate());
          event.setAmount(payment.getAmount());
          event.setAllocatedAmount(allocation.getAllocatedAmount());
          event.setReference(payment.getReference());
          paymentEvents.add(event);
        }
      }
      billDetail.setPaymentEvents(paymentEvents);

      billDetails.add(billDetail);
    }

    detailedStatement.setBillDetails(billDetails);

    // Save statement history
    SupplierStatementHistory history = createStatementHistory(
        companyId,
        supplierId,
        SupplierStatementHistory.StatementType.DETAILED,
        startDate,
        endDate,
        summaryStatement);
    historyRepository.save(history);
    
    // Update detailed statement DTO with persisted ID
    detailedStatement.setId(history.getId());

    // Log audit event
    try {
        auditService.logStatementGenerated(
            companyId,
            getCurrentUserId(),
            history.getId(),
            supplierId,
            SupplierStatementHistory.StatementType.DETAILED.name(),
            null);
    } catch (Exception e) {
        logger.error("Failed to log statement generation audit event", e);
    }

    return detailedStatement;
  }

  @Override
  public SupplierStatementHistoryDTO getStatementById(UUID statementId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    SupplierStatementHistory history =
        historyRepository
            .findByCompanyIdAndId(companyId, statementId)
            .orElseThrow(() -> new IllegalArgumentException("Statement not found: " + statementId));

    return convertToHistoryDTO(history);
  }

  @Override
  public Page<SupplierStatementHistoryDTO> findAllStatements(
      Long supplierId,
      LocalDate startDate,
      LocalDate endDate,
      SupplierStatementHistory.StatementType statementType,
      Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    Page<SupplierStatementHistory> historyPage;
    if (supplierId != null && statementType != null) {
      historyPage =
          historyRepository.findByCompanyIdAndSupplierIdAndStatementType(
              companyId, supplierId, statementType, pageable);
    } else if (supplierId != null) {
      historyPage =
          historyRepository.findByCompanyIdAndSupplierId(companyId, supplierId, pageable);
    } else {
      historyPage = historyRepository.findByCompanyId(companyId, pageable);
    }

    List<SupplierStatementHistoryDTO> dtos =
        historyPage.getContent().stream().map(this::convertToHistoryDTO).collect(Collectors.toList());

    return new PageImpl<>(dtos, pageable, historyPage.getTotalElements());
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public byte[] exportStatement(
      UUID statementId, SupplierStatementHistory.ExportFormat format) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    SupplierStatementHistory history =
        historyRepository
            .findByCompanyIdAndId(companyId, statementId)
            .orElseThrow(() -> new IllegalArgumentException("Statement not found: " + statementId));

    // Regenerate statement data
    SupplierStatementDTO statement =
        generateSummaryStatement(
            history.getSupplierId(), history.getStartDate(), history.getEndDate());

    byte[] exportedData;
    if (format == SupplierStatementHistory.ExportFormat.EXCEL) {
      exportedData = exportToExcel(statement);
    } else {
      exportedData = exportToPDF(statement);
    }

    // Update history
    history.incrementDownloadCount();
    historyRepository.save(history);

    // Log audit event
    auditService.logStatementExported(
        companyId,
        getCurrentUserId(),
        statementId,
        format.name(),
        null);

    return exportedData;
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public void sendStatementToSupplier(UUID statementId, List<String> recipientEmails) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    SupplierStatementHistory history =
        historyRepository
            .findByCompanyIdAndId(companyId, statementId)
            .orElseThrow(() -> new IllegalArgumentException("Statement not found: " + statementId));

    // Update sent information
    history.setSentDate(Instant.now());
    try {
      history.setSentTo(objectMapper.writeValueAsString(recipientEmails));
    } catch (JsonProcessingException e) {
      logger.error("Failed to serialize recipient emails", e);
    }
    historyRepository.save(history);

    // Log audit event (email service integration pending)
    auditService.logStatementSent(
        companyId,
        getCurrentUserId(),
        statementId,
        recipientEmails.size(),
        null);
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public void sendBatchStatements(List<UUID> statementIds, List<String> recipientEmails) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    for (UUID statementId : statementIds) {
      sendStatementToSupplier(statementId, recipientEmails);
    }

    // Log batch audit event
    logger.info("Sent {} statements to: {}", statementIds.size(), String.join(", ", recipientEmails));
  }

  @Override
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public byte[] downloadStatementBatch(List<UUID> statementIds) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    List<SupplierStatementHistory> statements =
        historyRepository.findByCompanyIdAndIdIn(companyId, statementIds);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (SupplierStatementHistory history : statements) {
        // Regenerate statement
        SupplierStatementDTO statement =
            generateSummaryStatement(
                history.getSupplierId(), history.getStartDate(), history.getEndDate());

        byte[] statementData = exportToExcel(statement);
        String filename =
            String.format(
                "statement-%s-%s.xlsx",
                statement.getSupplierCode(), DATE_FORMATTER.format(statement.getEndDate()));

        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(statementData);
        zos.closeEntry();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to create ZIP archive", e);
    }

    // Log audit event
    logger.info("Downloaded {} statements as ZIP", statementIds.size());

    return baos.toByteArray();
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ReconciliationResultDTO importSupplierStatement(
      Long supplierId, MultipartFile file, String format) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Parse file based on format
    List<ReconciliationResultDTO.ReconciliationItemDTO> supplierItems =
        parseSupplierStatementFile(file, format);

    // Get system bills for supplier
    List<PurchaseBill> systemBills =
        billRepository.findByCompanyId(companyId).stream()
            .filter(b -> b.getSupplierId().equals(supplierId))
            .collect(Collectors.toList());

    // Perform reconciliation
    ReconciliationResultDTO result = performReconciliation(supplierItems, systemBills);
    result.setSupplierId(supplierId);
    result.setReconciliationDate(LocalDate.now());

    Supplier supplier =
        supplierRepository
            .findByCompanyIdAndId(companyId, supplierId)
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
    result.setSupplierName(supplier.getName());

    // Log audit event
    auditService.logStatementImported(
        companyId,
        getCurrentUserId(),
        supplierId,
        result.getTotalItems(),
        result.getMismatchedCount(),
        null);

    return result;
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public void saveReconciliationResults(
      Long supplierId, ReconciliationResultDTO results, String notes) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    Long userId = getCurrentUserId();

    // Create disputes for mismatched items
    for (ReconciliationResultDTO.ReconciliationItemDTO item : results.getMismatched()) {
      SupplierStatementDispute dispute = new SupplierStatementDispute();
      dispute.setCompanyId(companyId);
      dispute.setSupplierId(supplierId);
      dispute.setBillId(item.getBillId());
      dispute.setBillNumber(item.getBillNumber());
      dispute.setDisputeReason("Amount mismatch: " + item.getNotes());
      dispute.setDisputedAmount(item.getSupplierAmount());
      dispute.setSystemAmount(item.getSystemAmount());
      dispute.setStatus(SupplierStatementDispute.DisputeStatus.OPEN);
      dispute.setCreatedBy(userId);
      disputeRepository.save(dispute);
    }

    // Create disputes for missing items
    for (ReconciliationResultDTO.ReconciliationItemDTO item : results.getMissing()) {
      SupplierStatementDispute dispute = new SupplierStatementDispute();
      dispute.setCompanyId(companyId);
      dispute.setSupplierId(supplierId);
      dispute.setBillNumber(item.getBillNumber());
      dispute.setDisputeReason("Bill not found in system: " + item.getNotes());
      dispute.setDisputedAmount(item.getSupplierAmount());
      dispute.setStatus(SupplierStatementDispute.DisputeStatus.OPEN);
      dispute.setCreatedBy(userId);
      disputeRepository.save(dispute);
    }

    // Log audit event
    auditService.logReconciliationSaved(
        companyId,
        userId,
        supplierId,
        results.getMismatchedCount() + results.getMissingCount(),
        null);
  }

  @Override
  @Transactional
  @PreAuthorize("hasAnyRole('ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public void updateDisputeLog(UUID disputeId, UpdateDisputeRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    SupplierStatementDispute dispute =
        disputeRepository
            .findByCompanyIdAndId(companyId, disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

    Long userId = getCurrentUserId();

    // Capture old status for audit
    String oldStatus = dispute.getStatus().name();

    // Update status and resolution
    if (request.getStatus() == SupplierStatementDispute.DisputeStatus.RESOLVED) {
      dispute.resolve(userId, request.getResolutionNotes());
    } else if (request.getStatus() == SupplierStatementDispute.DisputeStatus.REJECTED) {
      dispute.reject(userId, request.getResolutionNotes());
    } else {
      dispute.setStatus(request.getStatus());
      dispute.setResolutionNotes(request.getResolutionNotes());
    }

    disputeRepository.save(dispute);

    // Log audit event
    auditService.logDisputeUpdated(
        companyId,
        userId,
        disputeId,
        oldStatus,
        dispute.getStatus().name(),
        null);
  }

  @Override
  public SupplierStatementDisputeDTO getDisputeById(UUID disputeId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    SupplierStatementDispute dispute =
        disputeRepository
            .findByCompanyIdAndId(companyId, disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

    return convertToDisputeDTO(dispute);
  }

  @Override
  public Page<SupplierStatementDisputeDTO> findDisputes(
      Long supplierId, String status, Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    Page<SupplierStatementDispute> disputePage;
    if (supplierId != null && status != null) {
      SupplierStatementDispute.DisputeStatus disputeStatus =
          SupplierStatementDispute.DisputeStatus.valueOf(status);
      disputePage =
          disputeRepository.findByCompanyIdAndSupplierIdAndStatus(
              companyId, supplierId, disputeStatus, pageable);
    } else if (supplierId != null) {
      disputePage =
          disputeRepository.findByCompanyIdAndSupplierId(companyId, supplierId, pageable);
    } else if (status != null) {
      SupplierStatementDispute.DisputeStatus disputeStatus =
          SupplierStatementDispute.DisputeStatus.valueOf(status);
      disputePage =
          disputeRepository.findByCompanyIdAndStatus(companyId, disputeStatus, pageable);
    } else {
      disputePage = disputeRepository.findByCompanyId(companyId, pageable);
    }

    List<SupplierStatementDisputeDTO> dtos =
        disputePage.getContent().stream().map(this::convertToDisputeDTO).collect(Collectors.toList());

    return new PageImpl<>(dtos, pageable, disputePage.getTotalElements());
  }

  @Override
  public Page<SupplierStatementHistoryDTO> getStatementHistory(
      Long supplierId, Pageable pageable) {
    return findAllStatements(supplierId, null, null, null, pageable);
  }

  // Helper methods

  private List<PurchaseBill> getPostedBillsForSupplier(
      Long companyId, Long supplierId, LocalDate startDate, LocalDate endDate) {
    return billRepository.findByCompanyId(companyId).stream()
        .filter(b -> b.getSupplierId().equals(supplierId))
        .filter(
            b ->
                b.getStatus() == PurchaseBillStatus.POSTED
                    || b.getStatus() == PurchaseBillStatus.PAID
                    || b.getStatus() == PurchaseBillStatus.PARTIALLY_PAID)
        .filter(b -> !b.getBillDate().isBefore(startDate))
        .filter(b -> !b.getBillDate().isAfter(endDate))
        .sorted(Comparator.comparing(PurchaseBill::getBillDate))
        .collect(Collectors.toList());
  }

  private BigDecimal calculateOpeningBalance(
      Long companyId, Long supplierId, LocalDate beforeDate) {
    List<PurchaseBill> priorBills =
        billRepository.findByCompanyId(companyId).stream()
            .filter(b -> b.getSupplierId().equals(supplierId))
            .filter(
                b ->
                    b.getStatus() == PurchaseBillStatus.POSTED
                        || b.getStatus() == PurchaseBillStatus.PAID
                        || b.getStatus() == PurchaseBillStatus.PARTIALLY_PAID)
            .filter(b -> b.getBillDate().isBefore(beforeDate))
            .collect(Collectors.toList());

    BigDecimal opening = BigDecimal.ZERO;
    for (PurchaseBill bill : priorBills) {
      opening = opening.add(calculateRemainingBalance(bill.getId()));
    }
    return opening;
  }

  private BigDecimal calculateRemainingBalance(UUID billId) {
    List<PaymentAllocation> allocations = allocationRepository.findByPurchaseBillId(billId);
    BigDecimal totalAllocated =
        allocations.stream()
            .map(PaymentAllocation::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    PurchaseBill bill =
        billRepository
            .findById(billId)
            .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));
    return bill.getTotalAmount().subtract(totalAllocated);
  }

  private byte[] exportToExcel(SupplierStatementDTO statement) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Supplier Statement");

      // Create header style
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      int rowNum = 0;

      // Title
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("SUPPLIER STATEMENT");
      titleCell.setCellStyle(headerStyle);

      // Supplier info
      rowNum++;
      createRow(sheet, rowNum++, "Supplier:", statement.getSupplierName());
      createRow(sheet, rowNum++, "Code:", statement.getSupplierCode());
      createRow(
          sheet,
          rowNum++,
          "Period:",
          DATE_FORMATTER.format(statement.getStartDate())
              + " - "
              + DATE_FORMATTER.format(statement.getEndDate()));

      // Opening balance
      rowNum++;
      createRow(sheet, rowNum++, "Opening Balance:", formatCurrency(statement.getOpeningBalance()));

      // Table header
      rowNum++;
      Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {"Date", "Reference", "Description", "Debit", "Credit", "Balance"};
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (SupplierStatementDTO.StatementLineItemDTO item : statement.getItems()) {
        Row dataRow = sheet.createRow(rowNum++);
        dataRow.createCell(0).setCellValue(DATE_FORMATTER.format(item.getDate()));
        dataRow.createCell(1).setCellValue(item.getReference());
        dataRow.createCell(2).setCellValue(item.getDescription());
        dataRow.createCell(3).setCellValue(formatCurrency(item.getDebit()));
        dataRow.createCell(4).setCellValue(formatCurrency(item.getCredit()));
        dataRow.createCell(5).setCellValue(formatCurrency(item.getBalance()));
      }

      // Totals
      rowNum++;
      createRow(sheet, rowNum++, "Total Debits:", formatCurrency(statement.getTotalDebits()));
      createRow(sheet, rowNum++, "Total Credits:", formatCurrency(statement.getTotalCredits()));
      createRow(sheet, rowNum++, "Closing Balance:", formatCurrency(statement.getClosingBalance()));

      // Footer
      rowNum++;
      String footer =
          "Generated: "
              + DATE_FORMATTER.format(LocalDate.now())
              + " | Hash: "
              + generateHash(statement);
      createRow(sheet, rowNum++, footer, "");

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to export to Excel", e);
    }
  }

  private byte[] exportToPDF(SupplierStatementDTO statement) {
    // Simple text-based PDF for MVP (can be enhanced with iText or PDFBox later)
    StringBuilder pdf = new StringBuilder();
    pdf.append("SUPPLIER STATEMENT\n\n");
    pdf.append("Supplier: ").append(statement.getSupplierName()).append("\n");
    pdf.append("Code: ").append(statement.getSupplierCode()).append("\n");
    pdf.append("Period: ")
        .append(DATE_FORMATTER.format(statement.getStartDate()))
        .append(" - ")
        .append(DATE_FORMATTER.format(statement.getEndDate()))
        .append("\n\n");
    pdf.append("Opening Balance: ")
        .append(formatCurrency(statement.getOpeningBalance()))
        .append("\n\n");

    pdf.append(
        String.format(
            "%-12s %-20s %-30s %15s %15s %15s\n",
            "Date", "Reference", "Description", "Debit", "Credit", "Balance"));
    pdf.append("-".repeat(115)).append("\n");

    for (SupplierStatementDTO.StatementLineItemDTO item : statement.getItems()) {
      pdf.append(
          String.format(
              "%-12s %-20s %-30s %15s %15s %15s\n",
              DATE_FORMATTER.format(item.getDate()),
              truncate(item.getReference(), 20),
              truncate(item.getDescription(), 30),
              formatCurrency(item.getDebit()),
              formatCurrency(item.getCredit()),
              formatCurrency(item.getBalance())));
    }

    pdf.append("\n");
    pdf.append("Total Debits: ").append(formatCurrency(statement.getTotalDebits())).append("\n");
    pdf.append("Total Credits: ")
        .append(formatCurrency(statement.getTotalCredits()))
        .append("\n");
    pdf.append("Closing Balance: ")
        .append(formatCurrency(statement.getClosingBalance()))
        .append("\n\n");

    pdf.append("Generated: ")
        .append(DATE_FORMATTER.format(LocalDate.now()))
        .append(" | Hash: ")
        .append(generateHash(statement))
        .append("\n");

    return pdf.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String generateHash(SupplierStatementDTO statement) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String content =
          String.valueOf(statement.getSupplierId())
              + statement.getStartDate().toString()
              + statement.getEndDate().toString()
              + statement.getClosingBalance().toString();
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      return "HASH_ERROR";
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  private void createRow(Sheet sheet, int rowNum, String label, String value) {
    Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value);
  }

  private String formatCurrency(BigDecimal amount) {
    if (amount == null) {
      return "0₫";
    }
    return String.format("%,.0f₫", amount);
  }

  private String truncate(String str, int maxLength) {
    if (str == null) {
      return "";
    }
    return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
  }

  private List<ReconciliationResultDTO.ReconciliationItemDTO> parseSupplierStatementFile(
      MultipartFile file, String format) {
    // Simple Excel parsing (can be enhanced based on actual file structure)
    List<ReconciliationResultDTO.ReconciliationItemDTO> items = new ArrayList<>();

    if ("EXCEL".equalsIgnoreCase(format)) {
      try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);
        for (int i = 1; i < sheet.getLastRowNum(); i++) { // Skip header
          Row row = sheet.getRow(i);
          if (row != null) {
            ReconciliationResultDTO.ReconciliationItemDTO item =
                new ReconciliationResultDTO.ReconciliationItemDTO();
            item.setBillNumber(getCellValue(row.getCell(0)));
            item.setBillDate(LocalDate.parse(getCellValue(row.getCell(1)), DATE_FORMATTER));
            item.setSupplierAmount(new BigDecimal(getCellValue(row.getCell(2))));
            items.add(item);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to parse Excel file", e);
      }
    }

    return items;
  }

  private String getCellValue(Cell cell) {
    if (cell == null) {
      return "";
    }
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      default -> "";
    };
  }

  private ReconciliationResultDTO performReconciliation(
      List<ReconciliationResultDTO.ReconciliationItemDTO> supplierItems,
      List<PurchaseBill> systemBills) {
    ReconciliationResultDTO result = new ReconciliationResultDTO();
    result.setReconciliationDate(LocalDate.now());

    Map<String, PurchaseBill> systemBillMap =
        systemBills.stream()
            .collect(Collectors.toMap(PurchaseBill::getBillNumber, b -> b, (a, b) -> a));

    List<ReconciliationResultDTO.ReconciliationItemDTO> matched = new ArrayList<>();
    List<ReconciliationResultDTO.ReconciliationItemDTO> mismatched = new ArrayList<>();
    List<ReconciliationResultDTO.ReconciliationItemDTO> missing = new ArrayList<>();

    for (ReconciliationResultDTO.ReconciliationItemDTO supplierItem : supplierItems) {
      PurchaseBill systemBill = systemBillMap.get(supplierItem.getBillNumber());

      if (systemBill == null) {
        supplierItem.setStatus("MISSING");
        supplierItem.setNotes("Bill not found in system");
        missing.add(supplierItem);
      } else {
        supplierItem.setBillId(systemBill.getId());
        supplierItem.setSystemAmount(systemBill.getTotalAmount());
        BigDecimal variance =
            supplierItem.getSupplierAmount().subtract(supplierItem.getSystemAmount()).abs();
        supplierItem.setVariance(variance);

        if (variance.compareTo(TOLERANCE) <= 0) {
          supplierItem.setStatus("MATCHED");
          matched.add(supplierItem);
        } else {
          supplierItem.setStatus("MISMATCHED");
          supplierItem.setNotes(
              "Amount variance: " + formatCurrency(variance) + " (tolerance: " + TOLERANCE + ")");
          mismatched.add(supplierItem);
        }
        systemBillMap.remove(supplierItem.getBillNumber());
      }
    }

    // Remaining system bills are "applied" (not in supplier statement)
    List<ReconciliationResultDTO.ReconciliationItemDTO> applied = new ArrayList<>();
    for (PurchaseBill bill : systemBillMap.values()) {
      ReconciliationResultDTO.ReconciliationItemDTO item =
          new ReconciliationResultDTO.ReconciliationItemDTO();
      item.setBillId(bill.getId());
      item.setBillNumber(bill.getBillNumber());
      item.setBillDate(bill.getBillDate());
      item.setSystemAmount(bill.getTotalAmount());
      item.setStatus("APPLIED");
      item.setNotes("Bill in system but not in supplier statement");
      applied.add(item);
    }

    result.setMatched(matched);
    result.setMismatched(mismatched);
    result.setMissing(missing);
    result.setApplied(applied);
    result.setMatchedCount(matched.size());
    result.setMismatchedCount(mismatched.size());
    result.setMissingCount(missing.size());
    result.setAppliedCount(applied.size());
    result.setTotalItems(supplierItems.size());

    return result;
  }

  private void copyBasicStatementFields(
      SupplierStatementDTO source, DetailedStatementDTO target) {
    target.setId(source.getId());
    target.setSupplierId(source.getSupplierId());
    target.setSupplierName(source.getSupplierName());
    target.setSupplierCode(source.getSupplierCode());
    target.setStatementType(SupplierStatementHistory.StatementType.DETAILED);
    target.setStartDate(source.getStartDate());
    target.setEndDate(source.getEndDate());
    target.setGenerationDate(source.getGenerationDate());
    target.setGeneratedByName(source.getGeneratedByName());
    target.setFormat(source.getFormat());
    target.setOpeningBalance(source.getOpeningBalance());
    target.setClosingBalance(source.getClosingBalance());
    target.setTotalDebits(source.getTotalDebits());
    target.setTotalCredits(source.getTotalCredits());
    target.setItems(source.getItems());
  }

  private SupplierStatementHistoryDTO convertToHistoryDTO(SupplierStatementHistory history) {
    SupplierStatementHistoryDTO dto = new SupplierStatementHistoryDTO();
    dto.setId(history.getId());
    dto.setSupplierId(history.getSupplierId());
    dto.setStatementType(history.getStatementType());
    dto.setGenerationDate(history.getGenerationDate());
    dto.setFormat(history.getFormat());
    dto.setHash(history.getHash());
    dto.setSentDate(history.getSentDate());
    dto.setViewCount(history.getViewCount());
    dto.setDownloadCount(history.getDownloadCount());
    dto.setStartDate(history.getStartDate());
    dto.setEndDate(history.getEndDate());

    if (history.getSupplier() != null) {
      dto.setSupplierName(history.getSupplier().getName());
      dto.setSupplierCode(history.getSupplier().getCode());
    }

    if (history.getGeneratedByUser() != null) {
      dto.setGeneratedByName(history.getGeneratedByUser().getFullName());
    }

    if (history.getSentTo() != null) {
      try {
        List<String> emails =
            objectMapper.readValue(
                history.getSentTo(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        dto.setSentTo(emails);
      } catch (JsonProcessingException e) {
        logger.error("Failed to deserialize sent_to emails", e);
      }
    }

    return dto;
  }

  private SupplierStatementDisputeDTO convertToDisputeDTO(SupplierStatementDispute dispute) {
    SupplierStatementDisputeDTO dto = new SupplierStatementDisputeDTO();
    dto.setId(dispute.getId());
    dto.setSupplierId(dispute.getSupplierId());
    dto.setBillId(dispute.getBillId());
    dto.setBillNumber(dispute.getBillNumber());
    dto.setDisputeReason(dispute.getDisputeReason());
    dto.setStatus(dispute.getStatus());
    dto.setResolutionNotes(dispute.getResolutionNotes());
    dto.setCreatedAt(dispute.getCreatedAt());
    dto.setResolvedAt(dispute.getResolvedAt());
    dto.setDisputedAmount(dispute.getDisputedAmount());
    dto.setSystemAmount(dispute.getSystemAmount());

    if (dispute.getDisputedAmount() != null && dispute.getSystemAmount() != null) {
      dto.setVariance(dispute.getDisputedAmount().subtract(dispute.getSystemAmount()).abs());
    }

    if (dispute.getSupplier() != null) {
      dto.setSupplierName(dispute.getSupplier().getName());
      dto.setSupplierCode(dispute.getSupplier().getCode());
    }

    if (dispute.getCreatedByUser() != null) {
      dto.setCreatedByName(dispute.getCreatedByUser().getFullName());
    }

    if (dispute.getResolvedByUser() != null) {
      dto.setResolvedByName(dispute.getResolvedByUser().getFullName());
    }

    return dto;
  }

  private String getCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getName();
    }
    return "System";
  }

  private Long getCurrentUserId() {
    String username = getCurrentUsername();
    return userRepository.findByEmail(username).map(User::getId).orElse(null);
  }

  /**
   * Create and populate a SupplierStatementHistory entity from statement DTO.
   */
  private SupplierStatementHistory createStatementHistory(
      Long companyId,
      Long supplierId,
      SupplierStatementHistory.StatementType statementType,
      LocalDate startDate,
      LocalDate endDate,
      SupplierStatementDTO statement) {
    SupplierStatementHistory history = new SupplierStatementHistory();
    history.setCompanyId(companyId);
    history.setSupplierId(supplierId);
    history.setStatementType(statementType);
    history.setStartDate(startDate);
    history.setEndDate(endDate);
    history.setGenerationDate(Instant.now());
    history.setGeneratedBy(getCurrentUserId());
    history.setFormat(statement.getFormat() != null 
        ? statement.getFormat() 
        : SupplierStatementHistory.ExportFormat.EXCEL);
    history.setHash(generateHash(statement));
    history.setViewCount(0);
    history.setDownloadCount(0);
    return history;
  }
}

