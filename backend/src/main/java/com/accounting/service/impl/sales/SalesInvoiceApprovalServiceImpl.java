package com.accounting.service.impl.sales;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ApprovalWorkflow;
import com.accounting.entity.ApprovalWorkflowStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.ApprovalWorkflowRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.SalesInvoiceApprovalService;
import com.accounting.service.ARVATService;
import com.accounting.service.VATService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of SalesInvoiceApprovalService.
 * Handles sales invoice approval workflow with maker-checker pattern for AR
 * module.
 * Mirrors ApprovalWorkflowServiceImpl but for sales invoices instead of
 * purchase bills.
 */
@Service
@Transactional
public class SalesInvoiceApprovalServiceImpl implements SalesInvoiceApprovalService {

        private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("100000000.00"); // 100M VND

        private final ApprovalWorkflowRepository approvalWorkflowRepository;
        private final SalesInvoiceRepository salesInvoiceRepository;
        private final CompanySettingsService companySettingsService;
        private final PeriodManagementService periodManagementService;
        private final AuditService auditService;
        private final SalesInvoiceLineRepository salesInvoiceLineRepository;
        private final ChartOfAccountsRepository chartOfAccountsRepository;
        private final VoucherService voucherService;
        private final VoucherPostingService voucherPostingService;
        private final VATService vatService;
        private final ARVATService arVatService;
        private final com.accounting.service.ARAgingService arAgingService;

        public SalesInvoiceApprovalServiceImpl(
                        ApprovalWorkflowRepository approvalWorkflowRepository,
                        SalesInvoiceRepository salesInvoiceRepository,
                        CompanySettingsService companySettingsService,
                        PeriodManagementService periodManagementService,
                        AuditService auditService,
                        SalesInvoiceLineRepository salesInvoiceLineRepository,
                        ChartOfAccountsRepository chartOfAccountsRepository,
                        VoucherService voucherService,
                        VoucherPostingService voucherPostingService,
                        VATService vatService,
                        ARVATService arVatService,
                        com.accounting.service.ARAgingService arAgingService) {
                this.approvalWorkflowRepository = approvalWorkflowRepository;
                this.salesInvoiceRepository = salesInvoiceRepository;
                this.companySettingsService = companySettingsService;
                this.periodManagementService = periodManagementService;
                this.auditService = auditService;
                this.salesInvoiceLineRepository = salesInvoiceLineRepository;
                this.chartOfAccountsRepository = chartOfAccountsRepository;
                this.voucherService = voucherService;
                this.voucherPostingService = voucherPostingService;
                this.vatService = vatService;
                this.arVatService = arVatService;
                this.arAgingService = arAgingService;
        }

        @Override
        @Transactional(readOnly = true)
        public boolean checkApprovalRequired(SalesInvoice invoice) {
                if (invoice == null) {
                        return false;
                }

                // Check if invoice is marked as sensitive
                if (Boolean.TRUE.equals(invoice.getIsSensitive())) {
                        return true;
                }

                // Check if invoice amount exceeds threshold
                BigDecimal threshold = getApprovalThreshold();
                return invoice.getTotalAmount().compareTo(threshold) > 0;
        }

        @Override
        public ApprovalWorkflowDTO submitForApproval(UUID invoiceId, Long submitterId) {
                UUID targetInvoiceId = Objects.requireNonNull(invoiceId, "invoiceId is required");
                Long companyId = CompanyContext.getCompanyId();

                // Load invoice
                SalesInvoice invoice = salesInvoiceRepository
                                .findById(invoiceId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Sales invoice not found: " + invoiceId));

                // Validate invoice status
                if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
                        throw new IllegalStateException(
                                        "Invoice must be in DRAFT status to submit for approval. Current status: "
                                                        + invoice.getStatus());
                }

                // Validate approval is required
                if (!checkApprovalRequired(invoice)) {
                        throw new IllegalArgumentException(
                                        "Invoice does not require approval (amount: "
                                                        + invoice.getTotalAmount()
                                                        + ", threshold: "
                                                        + getApprovalThreshold()
                                                        + ", sensitive: "
                                                        + invoice.getIsSensitive()
                                                        + ")");
                }

                // Create approval workflow
                ApprovalWorkflow workflow = new ApprovalWorkflow();
                workflow.setCompanyId(companyId);
                workflow.setSalesInvoiceId(targetInvoiceId);
                workflow.setCreatedById(submitterId);
                workflow.setStatus(ApprovalWorkflowStatus.PENDING);
                workflow.setThresholdAmount(getApprovalThreshold());
                workflow.setBillAmount(invoice.getTotalAmount());
                workflow.setIsSensitive(invoice.getIsSensitive());

                workflow = approvalWorkflowRepository.save(workflow);

                // Update invoice status to PENDING_APPROVAL
                invoice.setStatus(SalesInvoiceStatus.PENDING_APPROVAL);
                salesInvoiceRepository.save(invoice);

                // Audit log
                auditService.logSalesInvoiceSubmittedForApproval(
                                companyId, submitterId, targetInvoiceId, invoice.getTotalAmount(),
                                getApprovalThreshold());

                // Notification: Alert approvers (Chief Accountant/CFO) about pending approval
                // TODO: Integrate with notification service when available (Story 5.x)
                // For now, log notification event for manual/in-app notification
                logNotification(
                                "APPROVAL_REQUESTED",
                                String.format(
                                                "Sales invoice %s requires approval (Amount: %s, Customer: %s)",
                                                invoice.getInvoiceNumber(),
                                                invoice.getTotalAmount(),
                                                invoice.getCustomer() != null ? invoice.getCustomer().getName()
                                                                : "Unknown"),
                                "CHIEF_ACCOUNTANT,CFO");

                return toDTO(workflow, invoice);
        }

        @Override
        public ApprovalWorkflowDTO approve(UUID workflowId, Long approverId, String reason) {
                UUID targetWorkflowId = Objects.requireNonNull(workflowId, "workflowId is required");
                Long companyId = CompanyContext.getCompanyId();

                // Load workflow
                ApprovalWorkflow workflow = approvalWorkflowRepository
                                .findById(targetWorkflowId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Approval workflow not found: " + workflowId));

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

                // Load invoice for period validation
                UUID invoiceId = Objects.requireNonNull(workflow.getSalesInvoiceId(),
                                "Sales invoice reference is missing");
                SalesInvoice invoice = salesInvoiceRepository
                                .findById(invoiceId)
                                .orElseThrow(
                                                () -> new IllegalStateException(
                                                                "Sales invoice not found: " + invoiceId));

                // Validate period is open (invoices have accounting period based on
                // invoice_date)
                if (!periodManagementService.isDateInOpenPeriod(invoice.getInvoiceDate())) {
                        String errorMessage = String.format(
                                        "Cannot approve invoice: Accounting period for date %s is closed or does not exist",
                                        invoice.getInvoiceDate());
                        auditService.logSalesInvoiceRejected(
                                        companyId, approverId, invoice.getId(),
                                        "Period validation failed: " + errorMessage);
                        throw new IllegalStateException(errorMessage);
                }

                List<SalesInvoiceLine> invoiceLines = salesInvoiceLineRepository
                                .findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());
                if (invoiceLines.isEmpty()) {
                        throw new IllegalStateException("Cannot approve invoice: no line items available for posting");
                }

                // Validate VAT sum using ARVATService (AC-VAT-003)
                com.accounting.dto.VATValidationResultDTO vatValidation = arVatService.validateVATSum(invoice);
                if (!vatValidation.isValid()) {
                        String errorMessage = vatValidation.getErrors().isEmpty()
                                        ? "VAT validation failed"
                                        : vatValidation.getErrors().get(0);
                        throw new IllegalStateException(
                                        "Cannot approve invoice: " + errorMessage);
                }

                UUID postedVoucherId = createAndPostVoucherForInvoice(companyId, invoice, invoiceLines);

                // Update workflow
                workflow.setStatus(ApprovalWorkflowStatus.APPROVED);
                workflow.setApprovedById(approverId);
                workflow.setApprovedAt(Instant.now());
                workflow.setApprovalReason(reason);

                workflow = approvalWorkflowRepository.save(workflow);

                // Update invoice status to POSTED and set approved_by
                invoice.setStatus(SalesInvoiceStatus.POSTED);
                invoice.setApprovedById(approverId);
                invoice.setPostedVoucherId(postedVoucherId);
                salesInvoiceRepository.save(invoice);

                // Invalidate AR aging cache when invoice is posted (new invoice affects aging
                // calculations)
                if (arAgingService != null) {
                        try {
                                arAgingService.invalidateAgingCache();
                        } catch (Exception e) {
                                org.slf4j.LoggerFactory.getLogger(SalesInvoiceApprovalServiceImpl.class)
                                                .warn("Failed to invalidate AR aging cache after invoice approval: {}",
                                                                e.getMessage());
                        }
                }

                // Audit log
                auditService.logSalesInvoiceApproved(companyId, approverId, invoice.getId(), reason);

                // Notification: Alert creator that invoice was approved
                logNotification(
                                "INVOICE_APPROVED",
                                String.format(
                                                "Sales invoice %s has been approved and posted (Approver: User#%d)",
                                                invoice.getInvoiceNumber(),
                                                approverId),
                                "CREATOR_" + workflow.getCreatedById());

                return toDTO(workflow, invoice);
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
                                                () -> new IllegalArgumentException(
                                                                "Approval workflow not found: " + workflowId));

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

                // Load invoice
                UUID invoiceId = Objects.requireNonNull(workflow.getSalesInvoiceId(),
                                "Sales invoice reference is missing");
                SalesInvoice invoice = salesInvoiceRepository
                                .findById(invoiceId)
                                .orElseThrow(
                                                () -> new IllegalStateException(
                                                                "Sales invoice not found: " + invoiceId));

                // Update workflow
                workflow.setStatus(ApprovalWorkflowStatus.REJECTED);
                workflow.setApprovedById(approverId);
                workflow.setRejectedAt(Instant.now());
                workflow.setRejectionReason(reason);

                workflow = approvalWorkflowRepository.save(workflow);

                // Update invoice status to DRAFT (so creator can edit and resubmit)
                invoice.setStatus(SalesInvoiceStatus.DRAFT);
                salesInvoiceRepository.save(invoice);

                // Audit log
                auditService.logSalesInvoiceRejected(companyId, approverId, invoice.getId(), reason);

                // Notification: Alert creator that invoice was rejected
                logNotification(
                                "INVOICE_REJECTED",
                                String.format(
                                                "Sales invoice %s has been rejected (Reason: %s, Approver: User#%d)",
                                                invoice.getInvoiceNumber(),
                                                reason,
                                                approverId),
                                "CREATOR_" + workflow.getCreatedById());

                return toDTO(workflow, invoice);
        }

        @Override
        public ApprovalWorkflowDTO autoApprove(SalesInvoice invoice, Long submitterId) {
                Long companyId = CompanyContext.getCompanyId();

                // Load invoice lines for voucher posting
                List<SalesInvoiceLine> invoiceLines = salesInvoiceLineRepository
                                .findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());
                if (invoiceLines.isEmpty()) {
                        throw new IllegalStateException(
                                        "Cannot auto-approve invoice: no line items available for posting");
                }

                // Post voucher for auto-approved invoice
                UUID postedVoucherId = createAndPostVoucherForInvoice(companyId, invoice, invoiceLines);

                // Update invoice status to POSTED and set voucher reference
                invoice.setStatus(SalesInvoiceStatus.POSTED);
                invoice.setApprovedById(submitterId);
                invoice.setPostedVoucherId(postedVoucherId);
                salesInvoiceRepository.save(invoice);

                // Create shadow workflow record for audit trail
                ApprovalWorkflow workflow = new ApprovalWorkflow();
                workflow.setCompanyId(companyId);
                workflow.setSalesInvoiceId(invoice.getId());
                workflow.setCreatedById(submitterId);
                // Note: approved_by_id is NULL for AUTO_APPROVED (system approval, not human)
                // This avoids violating chk_approval_workflows_approver_not_creator constraint
                workflow.setApprovedById(null);
                workflow.setStatus(ApprovalWorkflowStatus.AUTO_APPROVED);
                workflow.setThresholdAmount(getApprovalThreshold());
                workflow.setBillAmount(invoice.getTotalAmount());
                workflow.setIsSensitive(invoice.getIsSensitive());
                workflow.setApprovedAt(Instant.now());
                workflow.setApprovalReason("Auto-approved: amount below threshold and not marked sensitive");

                workflow = approvalWorkflowRepository.save(workflow);

                // Invalidate AR aging cache when invoice is posted
                // Note: ARAgingService will be implemented in Story 5.4
                // if (arAgingService != null) {
                // try {
                // arAgingService.invalidateAgingCache();
                // } catch (Exception e) {
                // org.slf4j.LoggerFactory.getLogger(SalesInvoiceApprovalServiceImpl.class)
                // .warn("Failed to invalidate AR aging cache after auto-approval: {}",
                // e.getMessage());
                // }
                // }

                // Audit log for auto-approval
                auditService.logSalesInvoiceAutoApproved(
                                companyId, submitterId, invoice.getId(), invoice.getTotalAmount(),
                                getApprovalThreshold());

                return toDTO(workflow, invoice);
        }

        @Override
        @Transactional(readOnly = true)
        public List<ApprovalWorkflowDTO> getPendingApprovals() {
                Long companyId = CompanyContext.getCompanyId();
                List<ApprovalWorkflow> workflows = approvalWorkflowRepository
                                .findByCompanyIdAndStatusAndSalesInvoiceIdIsNotNull(
                                                companyId, ApprovalWorkflowStatus.PENDING);

                return workflows.stream()
                                .map(
                                                wf -> {
                                                        SalesInvoice inv = null;
                                                        UUID invoiceIdRef = wf.getSalesInvoiceId();
                                                        if (invoiceIdRef != null) {
                                                                inv = salesInvoiceRepository.findById(invoiceIdRef)
                                                                                .orElse(null);
                                                        }
                                                        return toDTO(wf, inv);
                                                })
                                .collect(Collectors.toList());
        }

        @Override
        @Transactional(readOnly = true)
        public List<ApprovalWorkflowDTO> getApprovalHistory(UUID invoiceId) {
                UUID targetInvoiceId = Objects.requireNonNull(invoiceId, "invoiceId is required");
                List<ApprovalWorkflow> workflows = approvalWorkflowRepository.findBySalesInvoiceId(targetInvoiceId);

                SalesInvoice invoice = salesInvoiceRepository.findById(targetInvoiceId).orElse(null);

                return workflows.stream().map(workflow -> toDTO(workflow, invoice)).collect(Collectors.toList());
        }

        @Override
        @Transactional(readOnly = true)
        public long getPendingApprovalsCount() {
                Long companyId = CompanyContext.getCompanyId();
                return approvalWorkflowRepository.countByCompanyIdAndStatusAndSalesInvoiceIdIsNotNull(
                                companyId, ApprovalWorkflowStatus.PENDING);
        }

        /**
         * Get the approval threshold amount from company settings.
         * Returns default if not configured.
         */
        private BigDecimal getApprovalThreshold() {
                try {
                        var settings = companySettingsService.getCurrentCompanySettings();
                        if (settings.getSalesInvoiceApprovalThresholdAmount() != null) {
                                return settings.getSalesInvoiceApprovalThresholdAmount();
                        }
                } catch (Exception e) {
                        // Fall back to default if settings not available
                }
                return DEFAULT_THRESHOLD;
        }

        /**
         * Convert ApprovalWorkflow entity to DTO.
         */
        private ApprovalWorkflowDTO toDTO(ApprovalWorkflow workflow, SalesInvoice invoice) {
                ApprovalWorkflowDTO dto = new ApprovalWorkflowDTO(
                                workflow.getId(),
                                workflow.getCompanyId(),
                                null, // purchaseBillId
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

                dto.setSalesInvoiceId(workflow.getSalesInvoiceId());

                // Add nested info if available
                if (invoice != null) {
                        dto.setInvoiceNumber(invoice.getInvoiceNumber());
                        if (invoice.getCustomer() != null) {
                                dto.setCustomerName(invoice.getCustomer().getName());
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

        private UUID createAndPostVoucherForInvoice(
                        Long companyId, SalesInvoice invoice, List<SalesInvoiceLine> lines) {
                // Check if invoice already has a posted voucher (idempotency check -
                // AC-VAT-007)
                if (invoice.getPostedVoucherId() != null) {
                        // Invoice already posted - return existing voucher ID (idempotent)
                        org.slf4j.LoggerFactory.getLogger(SalesInvoiceApprovalServiceImpl.class)
                                        .info(
                                                        "Invoice {} already has posted voucher {}. Skipping duplicate posting (idempotency).",
                                                        invoice.getInvoiceNumber(),
                                                        invoice.getPostedVoucherId());
                        return invoice.getPostedVoucherId();
                }

                // Validate invoice status before posting (AC-VAT-007)
                if (invoice.getStatus() == SalesInvoiceStatus.POSTED) {
                        throw new IllegalStateException(
                                        "Invoice " + invoice.getInvoiceNumber()
                                                        + " is already POSTED. Cannot post again.");
                }

                // Generate GL splits using ARVATService (AC-VAT-002)
                List<VoucherEntryLineRequest> entryLines = arVatService.generateGLSplit(invoice);

                if (entryLines.isEmpty()) {
                        throw new IllegalStateException(
                                        "Sales invoice "
                                                        + invoice.getInvoiceNumber()
                                                        + " has no monetary value to post. Cannot create voucher.");
                }

                VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
                voucherRequest.setDate(invoice.getInvoiceDate());
                voucherRequest.setDescription(
                                String.format(
                                                "Sales invoice %s - %s",
                                                invoice.getInvoiceNumber(),
                                                invoice.getReference() != null ? invoice.getReference()
                                                                : "AR posting"));
                voucherRequest.setEntryLines(entryLines);

                VoucherDTO voucher = voucherService.create(voucherRequest);
                voucherPostingService.postVoucher(voucher.getId(), null);

                org.slf4j.LoggerFactory.getLogger(SalesInvoiceApprovalServiceImpl.class)
                                .info(
                                                "Created and posted voucher {} for sales invoice {}",
                                                voucher.getVoucherNumber(),
                                                invoice.getInvoiceNumber());

                return voucher.getId();
        }

        private VoucherEntryLineRequest buildEntryLine(
                        Long debitAccountId,
                        Long creditAccountId,
                        BigDecimal amount,
                        String description,
                        SalesInvoice invoice,
                        SalesInvoiceLine line) {
                VoucherEntryLineRequest entry = new VoucherEntryLineRequest();
                entry.setDebitAccountId(debitAccountId);
                entry.setCreditAccountId(creditAccountId);
                entry.setAmount(amount);
                entry.setDescription(description);
                entry.setCustomerId(invoice.getCustomerId());
                entry.setCostCenterId(line.getCostCenterId());
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

        /**
         * Log notification event for manual/in-app notification.
         * TODO: Integrate with notification service when available (Story 5.x)
         * This provides hooks for future notification integration.
         *
         * @param eventType   notification event type
         * @param message     notification message
         * @param targetRoles target user roles (comma-separated)
         */
        private void logNotification(String eventType, String message, String targetRoles) {
                org.slf4j.LoggerFactory.getLogger(SalesInvoiceApprovalServiceImpl.class)
                                .info(
                                                "[NOTIFICATION] Event: {}, Target: {}, Message: {}",
                                                eventType,
                                                targetRoles,
                                                message);
                // TODO: When notification service is available:
                // notificationService.sendInAppNotification(targetRoles, eventType, message);
                // if (emailEnabled) {
                // notificationService.sendEmailNotification(targetRoles, eventType, message);
                // }
        }
}
