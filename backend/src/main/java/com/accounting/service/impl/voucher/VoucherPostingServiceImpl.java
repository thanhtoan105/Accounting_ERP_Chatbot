package com.accounting.service.impl.voucher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.JournalEntryDTO;
import com.accounting.dto.PostVoucherResponse;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEmbeddingPayload;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.JournalEntry;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.exception.VoucherPostingException;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.N8nWebhookService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
import com.accounting.service.gl.JournalEntryService;
import com.accounting.service.util.VoucherAuditHelper;
import com.accounting.service.voucher.VoucherPostingService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of VoucherPostingService.
 * Handles atomic posting of vouchers with validation and journal entry generation.
 */
@Service
public class VoucherPostingServiceImpl implements VoucherPostingService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherPostingServiceImpl.class);

  private final VoucherRepository voucherRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final VoucherValidationService voucherValidationService;
  private final JournalEntryService journalEntryService;
  private final VoucherService voucherService;
  private final AuditService auditService;
  private final VoucherAuditHelper voucherAuditHelper;
  private final PeriodManagementService periodManagementService;
  private final N8nWebhookService n8nWebhookService;

  public VoucherPostingServiceImpl(
      VoucherRepository voucherRepository,
      VoucherLineRepository voucherLineRepository,
      VoucherValidationService voucherValidationService,
      JournalEntryService journalEntryService,
      VoucherService voucherService,
      AuditService auditService,
      VoucherAuditHelper voucherAuditHelper,
      PeriodManagementService periodManagementService,
      N8nWebhookService n8nWebhookService) {
    this.voucherRepository = voucherRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.voucherValidationService = voucherValidationService;
    this.journalEntryService = journalEntryService;
    this.voucherService = voucherService;
    this.auditService = auditService;
    this.voucherAuditHelper = voucherAuditHelper;
    this.periodManagementService = periodManagementService;
    this.n8nWebhookService = n8nWebhookService;
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public PostVoucherResponse postVoucher(UUID voucherId, HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Load voucher with company scoping
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Capture before snapshot for audit logging (draft state)
    com.fasterxml.jackson.databind.JsonNode beforeSnapshot = voucherAuditHelper.serializeVoucherToJson(voucher);

    // Validate voucher status is DRAFT
    if (!"draft".equals(voucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot post voucher: voucher must be in DRAFT status. Current status: " + voucher.getStatus());
    }

    // Validate period is open for voucher posting
    validatePeriodForVoucherPosting(voucher);

    // Build VoucherCreateRequest from existing voucher for validation
    VoucherCreateRequest validationRequest = buildValidationRequest(voucher);

    // Perform bulk validation - collect all errors at once
    VoucherValidationResult validationResult = voucherValidationService.validate(validationRequest);

    // If validation fails, throw exception with comprehensive error map
    if (!validationResult.isValid()) {
      Map<String, Object> errorMap = buildValidationErrorMap(validationResult);
      throw new VoucherPostingException("Validation failed", errorMap);
    }

    // All validation passed - proceed with posting in atomic transaction
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Instant now = Instant.now();

    // Update voucher status to POSTED
    voucher.setStatus("posted");
    voucher.setPostedBy(currentUserId);
    voucher.setPostedAt(now);
    voucher.setUpdatedAt(now);
    voucher = voucherRepository.save(voucher);

    // Generate journal entries
    List<JournalEntry> journalEntries = journalEntryService.generateJournalEntries(voucher);

    // Convert to DTOs
    VoucherDTO voucherDTO = voucherService.getVoucherById(voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve posted voucher"));

    List<JournalEntryDTO> journalEntryDTOs = journalEntries.stream()
        .map(this::toJournalEntryDTO)
        .collect(Collectors.toList());

    logger.info(
        "Voucher posted successfully. Voucher ID: {}, Voucher Number: {}, Journal Entries: {}",
        voucherId,
        voucher.getVoucherNumber(),
        journalEntries.size());

    // Enhanced audit logging for posting operation with JSON snapshots and diff hash
    try {
      com.fasterxml.jackson.databind.JsonNode afterSnapshot = voucherAuditHelper.serializeVoucherToJson(voucher);
      String diffHash = voucherAuditHelper.calculateDiffHash(beforeSnapshot, afterSnapshot);
      auditService.logVoucherEvent(
          voucherId,
          voucher.getVoucherNumber(),
          "VOUCHER_POSTED",
          beforeSnapshot,
          afterSnapshot,
          diffHash,
          request);
      } catch (Exception e) {
      // Non-blocking: log error but don't break main flow
        logger.error("Failed to log voucher posting to audit trail. Voucher ID: {}", voucherId, e);
    }

    // Trigger n8n webhook for voucher embedding (AC 9.0.1 - fire-and-forget, async)
    try {
      VoucherEmbeddingPayload embeddingPayload = buildEmbeddingPayload(voucher, voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucherId));
      n8nWebhookService.triggerEmbedding(embeddingPayload);
      logger.debug("Triggered n8n webhook for voucher embedding: voucherId={}, voucherNumber={}",
          voucherId, voucher.getVoucherNumber());
    } catch (Exception e) {
      // Non-blocking: log error but don't break voucher posting flow
      logger.error("Failed to trigger n8n webhook for voucher embedding. Voucher ID: {}, Voucher Number: {}",
          voucherId, voucher.getVoucherNumber(), e);
    }

    return new PostVoucherResponse(voucherDTO, journalEntryDTOs);
  }

  /**
   * Build VoucherCreateRequest from existing Voucher for validation.
   */
  private VoucherCreateRequest buildValidationRequest(Voucher voucher) {
    VoucherCreateRequest request = new VoucherCreateRequest();
    request.setDate(voucher.getVoucherDate());
    request.setDescription(voucher.getDescription());
    request.setPeriodId(voucher.getPeriodId());

    // Get voucher lines and convert to VoucherLineDTO
    List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucher.getId());
    List<VoucherLineDTO> lineDTOs = new ArrayList<>();

    for (VoucherLine line : lines) {
      VoucherLineDTO lineDTO = new VoucherLineDTO();
      lineDTO.setLineNumber(line.getLineNumber());
      lineDTO.setAccountId(line.getAccountId());
      lineDTO.setDebit(line.getDebit());
      lineDTO.setCredit(line.getCredit());
      lineDTO.setDescription(line.getDescription());
      lineDTO.setCustomerId(line.getCustomerId());
      lineDTO.setVendorId(line.getVendorId());
      lineDTO.setCostCenterId(line.getCostCenterId());
      lineDTOs.add(lineDTO);
    }

    request.setLines(lineDTOs);
    return request;
  }

  /**
   * Build comprehensive validation error map from validation result.
   */
  private Map<String, Object> buildValidationErrorMap(VoucherValidationResult result) {
    Map<String, Object> errorMap = new HashMap<>();
    Map<Integer, Map<String, List<String>>> lines = new HashMap<>();

    // Convert validation errors to error map format
    for (Map.Entry<Integer, Map<String, List<String>>> entry : result.getErrors().entrySet()) {
      Integer lineNumber = entry.getKey();
      Map<String, List<String>> fieldErrors = entry.getValue();
      lines.put(lineNumber, fieldErrors);
    }

    errorMap.put("lines", lines);
    return errorMap;
  }


  /**
   * Convert JournalEntry to JournalEntryDTO.
   */
  private JournalEntryDTO toJournalEntryDTO(JournalEntry entry) {
    JournalEntryDTO dto = new JournalEntryDTO();
    dto.setId(entry.getId());
    dto.setVoucherId(entry.getVoucherId());
    dto.setAccountId(entry.getAccountId());
    dto.setPeriodId(entry.getPeriodId());
    dto.setDebitAmount(entry.getDebitAmount());
    dto.setCreditAmount(entry.getCreditAmount());
    dto.setCustomerId(entry.getCustomerId());
    dto.setSupplierId(entry.getSupplierId());
    dto.setCostCenterId(entry.getCostCenterId());
    dto.setCompanyId(entry.getCompanyId());
    dto.setPostedAt(entry.getPostedAt());
    dto.setCreatedAt(entry.getCreatedAt());
    return dto;
  }

  /**
   * Validate that voucher period is open for posting.
   * Logs blocked attempts in audit trail.
   *
   * @param voucher voucher to validate
   * @throws ResponseStatusException if period is closed
   */
  private void validatePeriodForVoucherPosting(Voucher voucher) {
    try {
      // Validate that voucher date is in an open period
      if (!periodManagementService.isDateInOpenPeriod(voucher.getVoucherDate())) {
        // Find the period for this date to get period details for error message
        java.util.Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt = periodManagementService.findPeriodByDate(voucher.getVoucherDate());

        String periodName = periodOpt
            .map(com.accounting.dto.AccountingPeriodDTO::getPeriodName)
            .orElse("Unknown Period");

        // Log the blocked attempt in audit trail
        try {
          auditService.logPeriodValidationBlocked(
              periodOpt.map(com.accounting.dto.AccountingPeriodDTO::getId).orElse(null),
              "VOUCHER_POSTING",
              "Cannot post voucher in closed or future period: " + periodName
          );
        } catch (Exception e) {
          logger.error("Failed to log period validation block to audit trail", e);
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Cannot post voucher in closed or future period: " + periodName
        );
      }

      // If voucher has a specific period ID, also validate it's open
      if (voucher.getPeriodId() != null) {
        if (!periodManagementService.isPeriodOpen(voucher.getPeriodId())) {
          java.util.Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt = periodManagementService.getPeriodById(voucher.getPeriodId());

          String periodName = periodOpt
              .map(com.accounting.dto.AccountingPeriodDTO::getPeriodName)
              .orElse("Unknown Period");

          // Log the blocked attempt in audit trail
          try {
            auditService.logPeriodValidationBlocked(
                voucher.getPeriodId(),
                "VOUCHER_POSTING",
                "Cannot post voucher in closed period: " + periodName
            );
          } catch (Exception e) {
            logger.error("Failed to log period validation block to audit trail", e);
          }

          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Cannot post voucher in closed period: " + periodName
          );
        }
      }
    } catch (ResponseStatusException e) {
      // Re-throw ResponseStatusException (period closed)
      throw e;
    } catch (Exception e) {
      logger.error("Failed to validate period for voucher posting", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to validate period for voucher posting"
      );
    }
  }

  /**
   * Build VoucherEmbeddingPayload for n8n webhook trigger.
   * Constructs payload with voucher header, line items, and balance summary
   * for embedding generation and Pinecone upsert.
   *
   * @param voucher The posted voucher entity
   * @param lines Voucher line items
   * @return VoucherEmbeddingPayload for n8n webhook
   */
  private VoucherEmbeddingPayload buildEmbeddingPayload(Voucher voucher, List<VoucherLine> lines) {
    // Build header
    VoucherEmbeddingPayload.VoucherHeader header = new VoucherEmbeddingPayload.VoucherHeader(
        voucher.getVoucherNumber(),
        voucher.getVoucherDate(),
        voucher.getDescription(),
        voucher.getStatus()
    );

    // Build line items
    List<VoucherEmbeddingPayload.VoucherLine> lineItems = lines.stream()
        .map(line -> new VoucherEmbeddingPayload.VoucherLine(
            getAccountCode(line.getAccountId()),
            getAccountName(line.getAccountId()),
            line.getDebit(),
            line.getCredit(),
            line.getDescription()
        ))
        .collect(Collectors.toList());

    // Build balance summary
    VoucherEmbeddingPayload.BalanceSummary summary = new VoucherEmbeddingPayload.BalanceSummary(
        voucher.getTotalDebit(),
        voucher.getTotalCredit()
    );

    return new VoucherEmbeddingPayload(
        voucher.getCompanyId(),
        voucher.getId().toString(),
        header,
        lineItems,
        summary
    );
  }

  /**
   * Get account code by ID (placeholder - should be cached or injected service).
   * For MVP, returns placeholder string. Production should use ChartOfAccountsRepository.
   */
  private String getAccountCode(Long accountId) {
    // TODO: Inject ChartOfAccountsRepository and implement proper lookup with caching
    return accountId != null ? "ACC-" + accountId : "UNKNOWN";
  }

  /**
   * Get account name by ID (placeholder - should be cached or injected service).
   * For MVP, returns placeholder string. Production should use ChartOfAccountsRepository.
   */
  private String getAccountName(Long accountId) {
    // TODO: Inject ChartOfAccountsRepository and implement proper lookup with caching
    return accountId != null ? "Account " + accountId : "Unknown Account";
  }
}
