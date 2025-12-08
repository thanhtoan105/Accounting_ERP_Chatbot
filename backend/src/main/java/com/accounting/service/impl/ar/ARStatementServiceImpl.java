package com.accounting.service.impl.ar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARStatementHistoryDTO;
import com.accounting.entity.ARStatementDelivery;
import com.accounting.entity.ARStatementHistory;
import com.accounting.repository.ARStatementDeliveryRepository;
import com.accounting.repository.ARStatementHistoryRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARStatementCalculationService;
import com.accounting.service.ARStatementEmailService;
import com.accounting.service.ARStatementExportService;
import com.accounting.service.ARStatementService;
import com.accounting.service.AuditService;

/**
 * Implementation of ARStatementService for statement operations.
 */
@Service
@Transactional(readOnly = true)
public class ARStatementServiceImpl implements ARStatementService {

  private static final Logger logger = LoggerFactory.getLogger(ARStatementServiceImpl.class);

  private final ARStatementCalculationService calculationService;
  private final ARStatementExportService exportService;
  private final ARStatementEmailService emailService;
  private final ARStatementHistoryRepository historyRepository;
  private final ARStatementDeliveryRepository deliveryRepository;
  private final CustomerRepository customerRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;

  public ARStatementServiceImpl(
      ARStatementCalculationService calculationService,
      ARStatementExportService exportService,
      ARStatementEmailService emailService,
      ARStatementHistoryRepository historyRepository,
      ARStatementDeliveryRepository deliveryRepository,
      CustomerRepository customerRepository,
      UserRepository userRepository,
      AuditService auditService) {
    this.calculationService = calculationService;
    this.exportService = exportService;
    this.emailService = emailService;
    this.historyRepository = historyRepository;
    this.deliveryRepository = deliveryRepository;
    this.customerRepository = customerRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
  }

  @Override
  public Object getStatement(
      Long customerId,
      ARStatementHistory.StatementFormat format,
      LocalDate asOfDate) {
    StatementResult result = getStatementWithHistory(customerId, format, asOfDate);
    return result.getStatement();
  }

  @Override
  @Transactional
  public ARStatementService.StatementResult getStatementWithHistory(
      Long customerId,
      ARStatementHistory.StatementFormat format,
      LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }

    // Generate statement
    Object statement;
    if (format == ARStatementHistory.StatementFormat.SUMMARY) {
      statement = calculationService.generateSummaryStatement(customerId, asOfDate);
    } else {
      statement = calculationService.generateDetailedStatement(customerId, asOfDate);
    }

    // Save to history and return history ID
    UUID historyId = saveStatementHistory(customerId, format, asOfDate, statement);

    return new ARStatementService.StatementResult(statement, historyId);
  }

  @Override
  @Transactional
  public ARStatementService.ExportResult exportStatement(
      Long customerId,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }

    // Get statement with history
    StatementResult result = getStatementWithHistory(customerId, statementFormat, asOfDate);
    Object statement = result.getStatement();
    UUID historyId = result.getHistoryId();

    // Export
    byte[] exportData = exportService.exportStatement(statement, format, statementFormat);

    // Update export count in history
    var historyOpt = historyRepository.findById(historyId);
    if (historyOpt.isPresent()) {
      ARStatementHistory history = historyOpt.get();
      history.incrementExportCount();
      historyRepository.save(history);
    }

    return new ARStatementService.ExportResult(exportData, historyId);
  }

  @Override
  @Transactional
  public UUID sendStatementToCustomer(
      Long customerId,
      String recipientEmail,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }

    // Get statement with history
    StatementResult result = getStatementWithHistory(customerId, statementFormat, asOfDate);
    Object statement = result.getStatement();
    UUID historyId = result.getHistoryId();

    // Send email
    emailService.sendStatementEmail(customerId, recipientEmail, statement, statementFormat);

    // Create delivery record and update history
    var historyOpt = historyRepository.findById(historyId);
    if (historyOpt.isPresent()) {
      ARStatementHistory history = historyOpt.get();
      history.incrementSentCount();

      ARStatementDelivery delivery = new ARStatementDelivery();
      delivery.setCompanyId(companyId);
      delivery.setStatementId(historyId);
      delivery.setCustomerId(customerId);
      delivery.setRecipientEmail(recipientEmail);
      delivery.setStatus(ARStatementDelivery.DeliveryStatus.SENT);
      deliveryRepository.save(delivery);

      historyRepository.save(history);
    }

    return historyId;
  }

  @Override
  @Transactional
  public byte[] batchExportStatements(
      List<Long> customerIds,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate) {
    return exportService.batchExportStatements(customerIds, format, statementFormat, asOfDate);
  }

  @Override
  public Page<ARStatementHistoryDTO> getStatementHistory(Long customerId, Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    var historyPage = historyRepository.findByCustomerIdAndCompanyId(customerId, companyId, pageable);

    List<ARStatementHistoryDTO> dtos = historyPage.getContent().stream()
        .map(this::toHistoryDTO)
        .collect(Collectors.toList());

    return new PageImpl<>(dtos, pageable, historyPage.getTotalElements());
  }

  @Override
  public Object regenerateStatement(UUID statementId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    var historyOpt = historyRepository.findByIdAndCompanyId(statementId, companyId);
    if (historyOpt.isEmpty()) {
      throw new IllegalArgumentException("Statement history not found: " + statementId);
    }

    ARStatementHistory history = historyOpt.get();
    return getStatement(history.getCustomerId(), history.getFormatEnum(), history.getAsOfDate());
  }

  private UUID saveStatementHistory(
      Long customerId,
      ARStatementHistory.StatementFormat format,
      LocalDate asOfDate,
      Object statement) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ARStatementHistory history = new ARStatementHistory();
    history.setCompanyId(companyId);
    history.setCustomerId(customerId);
    history.setFormatEnum(format);
    history.setGeneratedAt(Instant.now());
    history.setGeneratedById(userId);
    history.setAsOfDate(asOfDate);

    // Generate statement hash
    try {
      String statementContent = statement.toString(); // Simplified - should serialize properly
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(statementContent.getBytes(StandardCharsets.UTF_8));
      StringBuilder hashHex = new StringBuilder();
      for (byte b : hashBytes) {
        hashHex.append(String.format("%02x", b));
      }
      history.setStatementHash(hashHex.toString());
    } catch (NoSuchAlgorithmException e) {
      logger.error("Failed to generate statement hash", e);
      history.setStatementHash("ERROR");
    }

    ARStatementHistory savedHistory = historyRepository.save(history);
    return savedHistory.getId();
  }

  private ARStatementHistoryDTO toHistoryDTO(ARStatementHistory history) {
    ARStatementHistoryDTO dto = new ARStatementHistoryDTO();
    dto.setId(history.getId());
    dto.setCustomerId(history.getCustomerId());
    if (history.getCustomer() != null) {
      dto.setCustomerName(history.getCustomer().getName());
    }
    dto.setStatementNumber(history.getStatementNumber());
    dto.setFormat(history.getFormatEnum());
    dto.setGeneratedAt(history.getGeneratedAt());
    dto.setGeneratedById(history.getGeneratedById());
    if (history.getGeneratedBy() != null) {
      dto.setGeneratedByName(history.getGeneratedBy().getFullName());
    }
    dto.setAsOfDate(history.getAsOfDate());
    dto.setExportCount(history.getExportCount());
    dto.setSentCount(history.getSentCount());
    dto.setStatementHash(history.getStatementHash());
    return dto;
  }
}
