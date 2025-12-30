package com.accounting.service.impl.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.dto.VATValidationResultDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ApprovalWorkflow;
import com.accounting.entity.ApprovalWorkflowStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.ApprovalWorkflowRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ApprovalWorkflowService;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;

/**
 * Implementation of ApprovalWorkflowService.
 * Handles purchase bill approval workflow with maker-checker pattern.
 */
@Service
@Transactional
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

  private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("20000000.00"); // 20M VND

  private final ApprovalWorkflowRepository approvalWorkflowRepository;
  private final PurchaseBillRepository purchaseBillRepository;
  private final CompanySettingsService companySettingsService;
  private final PeriodManagementService periodManagementService;
  private final AuditService auditService;
  private final PurchaseBillLineRepository purchaseBillLineRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final VoucherService voucherService;
  private final VoucherPostingService voucherPostingService;
  private final com.accounting.service.VATService vatService;
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.accounting.service.APAgingService agingService;

  public ApprovalWorkflowServiceImpl(
      ApprovalWorkflowRepository approvalWorkflowRepository,
      PurchaseBillRepository purchaseBillRepository,
      CompanySettingsService companySettingsService,
      PeriodManagementService periodManagementService,
      AuditService auditService,
      PurchaseBillLineRepository purchaseBillLineRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      VoucherService voucherService,
      VoucherPostingService voucherPostingService,
      com.accounting.service.VATService vatService) {
    this.approvalWorkflowRepository = approvalWorkflowRepository;
    this.purchaseBillRepository = purchaseBillRepository;
    this.companySettingsService = companySettingsService;
    this.periodManagementService = periodManagementService;
    this.auditService = auditService;
    this.purchaseBillLineRepository = purchaseBillLineRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.voucherService = voucherService;
    this.voucherPostingService = voucherPostingService;
    this.vatService = vatService;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean checkApprovalRequired(PurchaseBill bill) {
    if (bill == null) {
      return false;
    }

    // Check if bill is marked as sensitive
    if (Boolean.TRUE.equals(bill.getIsSensitive())) {
      return true;
    }

    // Check if bill amount exceeds threshold
    BigDecimal threshold = getApprovalThreshold();
    return bill.getTotalAmount().compareTo(threshold) > 0;
  }

  @Override
  public ApprovalWorkflowDTO submitForApproval(UUID billId, Long submitterId) {
    UUID targetBillId = Objects.requireNonNull(billId, "billId is required");
    Long companyId = CompanyContext.getCompanyId();

    // Load bill
    PurchaseBill bill = purchaseBillRepository
        .findById(billId)
        .orElseThrow(
            () -> new IllegalArgumentException("Purchase bill not found: " + billId));

    // Validate bill status
    if (bill.getStatus() != PurchaseBillStatus.DRAFT) {
      throw new IllegalStateException(
          "Bill must be in DRAFT status to submit for approval. Current status: "
              + bill.getStatus());
    }

    // Validate approval is required
    if (!checkApprovalRequired(bill)) {
      throw new IllegalArgumentException(
          "Bill does not require approval (amount: "
              + bill.getTotalAmount()
              + ", threshold: "
              + getApprovalThreshold()
              + ", sensitive: "
              + bill.getIsSensitive()
              + ")");
    }

    // Create approval workflow
    ApprovalWorkflow workflow = new ApprovalWorkflow();
    workflow.setCompanyId(companyId);
    workflow.setPurchaseBillId(targetBillId);
    workflow.setCreatedById(submitterId);
    workflow.setStatus(ApprovalWorkflowStatus.PENDING);
    workflow.setThresholdAmount(getApprovalThreshold());
    workflow.setBillAmount(bill.getTotalAmount());
    workflow.setIsSensitive(bill.getIsSensitive());

    workflow = approvalWorkflowRepository.save(workflow);

    // Update bill status to PENDING_APPROVAL
    bill.setStatus(PurchaseBillStatus.PENDING_APPROVAL);
    purchaseBillRepository.save(bill);

    // Audit log
    auditService.logPurchaseBillSubmittedForApproval(
        companyId, submitterId, targetBillId, bill.getTotalAmount(), getApprovalThreshold());

    return toDTO(workflow, bill);
  }

  @Override
  public ApprovalWorkflowDTO approve(UUID workflowId, Long approverId, String reason) {
    UUID targetWorkflowId = Objects.requireNonNull(workflowId, "workflowId is required");
    Long companyId = CompanyContext.getCompanyId();

    // Load workflow
    ApprovalWorkflow workflow = approvalWorkflowRepository
        .findById(targetWorkflowId)
        .orElseThrow(
            () -> new IllegalArgumentException("Approval workflow not found: " + workflowId));

    // Validate workflow status
    if (workflow.getStatus() != ApprovalWorkflowStatus.PENDING) {
      throw new IllegalStateException(
          "Workflow must be in PENDING status to approve. Current status: "
              + workflow.getStatus());
    }

    // Validate approver ≠ creator (maker-checker)
    if (approverId.equals(workflow.getCreatedById())) {
      throw new IllegalArgumentException(
          "Approver cannot be the same as creator (maker-checker violation). Creator ID: "
              + workflow.getCreatedById()
              + ", Approver ID: "
              + approverId);
    }

    // Load bill for period validation
    UUID billId = Objects.requireNonNull(workflow.getPurchaseBillId(), "Purchase bill reference is missing");
    PurchaseBill bill = purchaseBillRepository
        .findById(billId)
        .orElseThrow(
            () -> new IllegalStateException(
                "Purchase bill not found: " + billId));

    // Validate period is open (bills have accounting period based on bill_date)
    if (!periodManagementService.isDateInOpenPeriod(bill.getBillDate())) {
      String errorMessage = String.format(
          "Cannot approve bill: Accounting period for date %s is closed or does not exist",
          bill.getBillDate());
      auditService.logPurchaseBillRejected(
          companyId, approverId, bill.getId(), "Period validation failed: " + errorMessage);
      throw new IllegalStateException(errorMessage);
    }

    List<PurchaseBillLine> billLines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        companyId, bill.getId());
    if (billLines.isEmpty()) {
      throw new IllegalStateException("Cannot approve bill: no line items available for posting");
    }

    VATValidationResultDTO vatValidation = vatService.validateVATSum(bill);
    if (!vatValidation.isValid()) {
      String errorMessage = vatValidation.getErrors().isEmpty()
          ? "VAT validation failed"
          : vatValidation.getErrors().get(0);
      throw new IllegalStateException(
          "Cannot approve bill: " + errorMessage);
    }

    UUID postedVoucherId = createAndPostVoucherForBill(companyId, bill, billLines);

    // Update workflow
    workflow.setStatus(ApprovalWorkflowStatus.APPROVED);
    workflow.setApprovedById(approverId);
    workflow.setApprovedAt(Instant.now());
    workflow.setApprovalReason(reason);

    workflow = approvalWorkflowRepository.save(workflow);

    // Update bill status to POSTED and set approved_by
    bill.setStatus(PurchaseBillStatus.POSTED);
    bill.setApprovedById(approverId);
    bill.setPostedVoucherId(postedVoucherId);
    purchaseBillRepository.save(bill);

    // Invalidate aging cache when bill is posted (new bill affects aging
    // calculations)
    if (agingService != null
        && agingService instanceof com.accounting.service.impl.ap.APAgingServiceImpl) {
      try {
        ((com.accounting.service.impl.ap.APAgingServiceImpl) agingService).invalidateAgingCache();
      } catch (Exception e) {
        // Log but don't fail - cache invalidation is best effort
        org.slf4j.LoggerFactory.getLogger(ApprovalWorkflowServiceImpl.class)
            .warn("Failed to invalidate aging cache after bill approval: {}", e.getMessage());
      }
    }

    // Audit log
    auditService.logPurchaseBillApproved(companyId, approverId, bill.getId(), reason);

    return toDTO(workflow, bill);
  }

  @Override
  public ApprovalWorkflowDTO reject(UUID workflowId, Long approverId, String reason) {
    UUID targetWorkflowId = Objects.requireNonNull(workflowId, "workflowId is required");
    Long companyId = CompanyContext.getCompanyId();

    // Validate reason is provided
    if (reason == null || reason.trim().isEmpty()) {
      throw new IllegalArgumentException("Rejection reason is mandatory");
    }

    // Load workflow
    ApprovalWorkflow workflow = approvalWorkflowRepository
        .findById(targetWorkflowId)
        .orElseThrow(
            () -> new IllegalArgumentException("Approval workflow not found: " + workflowId));

    // Validate workflow status
    if (workflow.getStatus() != ApprovalWorkflowStatus.PENDING) {
      throw new IllegalStateException(
          "Workflow must be in PENDING status to reject. Current status: "
              + workflow.getStatus());
    }

    // Validate approver ≠ creator (maker-checker)
    if (approverId.equals(workflow.getCreatedById())) {
      throw new IllegalArgumentException(
          "Approver cannot be the same as creator (maker-checker violation). Creator ID: "
              + workflow.getCreatedById()
              + ", Approver ID: "
              + approverId);
    }

    // Load bill
    UUID billId = Objects.requireNonNull(workflow.getPurchaseBillId(), "Purchase bill reference is missing");
    PurchaseBill bill = purchaseBillRepository
        .findById(billId)
        .orElseThrow(
            () -> new IllegalStateException(
                "Purchase bill not found: " + billId));

    // Update workflow
    workflow.setStatus(ApprovalWorkflowStatus.REJECTED);
    workflow.setApprovedById(approverId);
    workflow.setRejectedAt(Instant.now());
    workflow.setRejectionReason(reason);

    workflow = approvalWorkflowRepository.save(workflow);

    // Update bill status to REJECTED
    bill.setStatus(PurchaseBillStatus.REJECTED);
    purchaseBillRepository.save(bill);

    // Audit log
    auditService.logPurchaseBillRejected(companyId, approverId, bill.getId(), reason);

    return toDTO(workflow, bill);
  }

  @Override
  public ApprovalWorkflowDTO autoApprove(PurchaseBill bill, Long submitterId) {
    Long companyId = CompanyContext.getCompanyId();

    // Create shadow workflow record for audit trail
    ApprovalWorkflow workflow = new ApprovalWorkflow();
    workflow.setCompanyId(companyId);
    workflow.setPurchaseBillId(bill.getId());
    workflow.setCreatedById(submitterId);
    // Note: approved_by_id is NULL for AUTO_APPROVED (system approval, not human)
    // This avoids violating chk_approval_workflows_approver_not_creator constraint
    workflow.setApprovedById(null);
    workflow.setStatus(ApprovalWorkflowStatus.AUTO_APPROVED);
    workflow.setThresholdAmount(getApprovalThreshold());
    workflow.setBillAmount(bill.getTotalAmount());
    workflow.setIsSensitive(bill.getIsSensitive());
    workflow.setApprovedAt(Instant.now());
    workflow.setApprovalReason("Auto-approved: amount below threshold and not marked sensitive");

    workflow = approvalWorkflowRepository.save(workflow);

    // Audit log for auto-approval
    auditService.logPurchaseBillAutoApproved(
        companyId, submitterId, bill.getId(), bill.getTotalAmount(), getApprovalThreshold());

    return toDTO(workflow, bill);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApprovalWorkflowDTO> getPendingApprovals() {
    List<ApprovalWorkflow> workflows = approvalWorkflowRepository.findByStatus(ApprovalWorkflowStatus.PENDING);

    return workflows.stream()
        .map(
            workflow -> {
              PurchaseBill bill = null;
              UUID billIdRef = workflow.getPurchaseBillId();
              if (billIdRef != null) {
                bill = purchaseBillRepository.findById(billIdRef).orElse(null);
              }
              return toDTO(workflow, bill);
            })
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApprovalWorkflowDTO> getApprovalHistory(UUID billId) {
    UUID targetBillId = Objects.requireNonNull(billId, "billId is required");
    List<ApprovalWorkflow> workflows = approvalWorkflowRepository.findByPurchaseBillId(targetBillId);

    PurchaseBill bill = purchaseBillRepository.findById(targetBillId).orElse(null);

    return workflows.stream().map(workflow -> toDTO(workflow, bill)).collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public long getPendingApprovalsCount() {
    return approvalWorkflowRepository.countPending();
  }

  /**
   * Get the approval threshold amount from company settings.
   * Returns default if not configured.
   */
  private BigDecimal getApprovalThreshold() {
    try {
      var settings = companySettingsService.getCurrentCompanySettings();
      if (settings.getApprovalThresholdAmount() != null) {
        return settings.getApprovalThresholdAmount();
      }
    } catch (Exception e) {
      // Fall back to default if settings not available
    }
    return DEFAULT_THRESHOLD;
  }

  /**
   * Convert ApprovalWorkflow entity to DTO.
   */
  private ApprovalWorkflowDTO toDTO(ApprovalWorkflow workflow, PurchaseBill bill) {
    ApprovalWorkflowDTO dto = new ApprovalWorkflowDTO(
        workflow.getId(),
        workflow.getCompanyId(),
        workflow.getPurchaseBillId(),
        workflow.getCreatedById(),
        workflow.getApprovedById(),
        workflow.getStatus(),
        workflow.getThresholdAmount(),
        workflow.getBillAmount(),
        workflow.getIsSensitive(),
        workflow.getApprovalReason(),
        workflow.getRejectionReason(),
        workflow.getCreatedAt(),
        workflow.getUpdatedAt(),
        workflow.getApprovedAt(),
        workflow.getRejectedAt());

    // Add nested info if available
    if (bill != null) {
      dto.setBillNumber(bill.getBillNumber());
      if (bill.getSupplier() != null) {
        dto.setSupplierName(bill.getSupplier().getName());
      }
    }

    if (workflow.getCreatedBy() != null) {
      dto.setCreatedByName(workflow.getCreatedBy().getFullName());
    }

    if (workflow.getApprovedBy() != null) {
      dto.setApprovedByName(workflow.getApprovedBy().getFullName());
    }

    return dto;
  }

  private UUID createAndPostVoucherForBill(
      Long companyId, PurchaseBill bill, List<PurchaseBillLine> lines) {
    Long apAccountId = getAccountIdByCode(companyId, "331", "Accounts Payable (331)");
    Long vatAccountId = getAccountIdByCode(companyId, "3331", "VAT Input (3331)");

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    for (PurchaseBillLine line : lines) {
      BigDecimal baseAmount = safe(line.getAmount());
      BigDecimal vatAmount = safe(line.getVatAmount());

      if (baseAmount.compareTo(BigDecimal.ZERO) > 0) {
        if (line.getAccountId() == null) {
          throw new IllegalStateException(
              "Purchase bill line "
                  + line.getLineNumber()
                  + " is missing an account. Cannot create voucher.");
        }
        entryLines.add(
            buildEntryLine(
                line.getAccountId(),
                apAccountId,
                baseAmount,
                String.format("Bill %s - line %d", bill.getBillNumber(), line.getLineNumber()),
                bill,
                line));
      }

      if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
        entryLines.add(
            buildEntryLine(
                vatAccountId,
                apAccountId,
                vatAmount,
                String.format(
                    "VAT for bill %s - line %d", bill.getBillNumber(), line.getLineNumber()),
                bill,
                line));
      }
    }

    if (entryLines.isEmpty()) {
      throw new IllegalStateException(
          "Purchase bill "
              + bill.getBillNumber()
              + " has no monetary value to post. Cannot create voucher.");
    }

    VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
    voucherRequest.setDate(bill.getBillDate());
    voucherRequest.setDescription(
        String.format(
            "Purchase bill %s - %s",
            bill.getBillNumber(), bill.getReference() != null ? bill.getReference() : "AP posting"));
    voucherRequest.setEntryLines(entryLines);

    VoucherDTO voucher = voucherService.create(voucherRequest);
    voucherPostingService.postVoucher(voucher.getId(), null);

    org.slf4j.LoggerFactory.getLogger(ApprovalWorkflowServiceImpl.class)
        .info(
            "Created and posted voucher {} for purchase bill {}",
            voucher.getVoucherNumber(),
            bill.getBillNumber());

    return voucher.getId();
  }

  private VoucherEntryLineRequest buildEntryLine(
      Long debitAccountId,
      Long creditAccountId,
      BigDecimal amount,
      String description,
      PurchaseBill bill,
      PurchaseBillLine line) {
    VoucherEntryLineRequest entry = new VoucherEntryLineRequest();
    entry.setDebitAccountId(debitAccountId);
    entry.setCreditAccountId(creditAccountId);
    entry.setAmount(amount);
    entry.setDescription(description);
    entry.setSupplierId(bill.getSupplierId());
    entry.setItemId(line.getItemId());
    return entry;
  }

  private Long getAccountIdByCode(Long companyId, String code, String label) {
    return chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, code)
        .map(account -> account.getId())
        .orElseThrow(
            () -> new IllegalStateException(
                label
                    + " not found in chart of accounts. Please ensure TT200 accounts are configured."));
  }

  private BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
