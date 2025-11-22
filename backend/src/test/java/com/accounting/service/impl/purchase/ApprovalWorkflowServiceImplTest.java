package com.accounting.service.impl.purchase;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.dto.CompanySettingsDto;
import com.accounting.entity.*;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ApprovalWorkflowRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApprovalWorkflowServiceImpl.
 * Tests the maker-checker approval workflow logic including:
 * - Threshold checking
 * - Approval/rejection workflows
 * - Approver ≠ creator validation
 * - Auto-approval logic
 * - Audit logging integration
 */
@ExtendWith(MockitoExtension.class)
public class ApprovalWorkflowServiceImplTest {

    @Mock
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Mock
    private PurchaseBillRepository purchaseBillRepository;

    @Mock
    private PurchaseBillLineRepository purchaseBillLineRepository;

    @Mock
    private CompanySettingsService companySettingsService;

    @Mock
    private AuditService auditService;

    @Mock
    private PeriodManagementService periodManagementService;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private VoucherPostingService voucherPostingService;

    @Mock
    private com.accounting.service.VATService vatService;

    @InjectMocks
    private ApprovalWorkflowServiceImpl approvalWorkflowService;

    private PurchaseBill testBill;
    private ApprovalWorkflow testWorkflow;
    private CompanySettingsDto companySettings;
    private static final Long COMPANY_ID = 1L;
    private static final Long CREATOR_USER_ID = 100L;
    private static final Long APPROVER_USER_ID = 200L;
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("20000000.00");

    @BeforeEach
    void setUp() {
        // Setup company context
        CompanyContext.setCompanyId(COMPANY_ID);

        // Setup company settings with threshold
        companySettings = new CompanySettingsDto();
        companySettings.setId(1L);
        companySettings.setCompanyId(COMPANY_ID);
        companySettings.setApprovalThresholdAmount(DEFAULT_THRESHOLD);

        // Setup test purchase bill
        testBill = new PurchaseBill();
        testBill.setId(UUID.randomUUID());
        testBill.setCompanyId(COMPANY_ID);
        testBill.setBillNumber("BILL-2025-001");
        testBill.setTotalAmount(new BigDecimal("25000000.00")); // Above threshold
        testBill.setStatus(PurchaseBillStatus.DRAFT);
        testBill.setCreatedById(CREATOR_USER_ID);
        testBill.setIsSensitive(false);

        // Setup test workflow
        testWorkflow = new ApprovalWorkflow();
        testWorkflow.setId(UUID.randomUUID());
        testWorkflow.setCompanyId(COMPANY_ID);
        testWorkflow.setPurchaseBillId(testBill.getId());
        testWorkflow.setCreatedById(CREATOR_USER_ID);
        testWorkflow.setStatus(ApprovalWorkflowStatus.PENDING);
        testWorkflow.setThresholdAmount(DEFAULT_THRESHOLD);
        testWorkflow.setBillAmount(testBill.getTotalAmount());
        testWorkflow.setIsSensitive(false);
        testWorkflow.setCreatedAt(Instant.now());
        testWorkflow.setUpdatedAt(Instant.now());

        lenient().when(periodManagementService.isDateInOpenPeriod(any())).thenReturn(true);
        lenient().when(vatService.validateVATSum(any(com.accounting.entity.PurchaseBill.class)))
                .thenReturn(new com.accounting.dto.VATValidationResultDTO(true));
    }

    // ==================== checkApprovalRequired Tests ====================

    @Test
    void checkApprovalRequired_shouldReturnTrue_whenBillAmountExceedsThreshold() {
        // Given: Bill with amount above threshold
        when(companySettingsService.getCurrentCompanySettings()).thenReturn(companySettings);
        testBill.setTotalAmount(new BigDecimal("25000000.00")); // Above 20M threshold
        testBill.setIsSensitive(false);

        // When
        boolean result = approvalWorkflowService.checkApprovalRequired(testBill);

        // Then
        assertTrue(result, "Approval should be required for bill amount exceeding threshold");
        verify(companySettingsService).getCurrentCompanySettings();
    }

    @Test
    void checkApprovalRequired_shouldReturnFalse_whenBillAmountBelowThreshold() {
        // Given: Bill with amount below threshold
        when(companySettingsService.getCurrentCompanySettings()).thenReturn(companySettings);
        testBill.setTotalAmount(new BigDecimal("15000000.00")); // Below 20M threshold
        testBill.setIsSensitive(false);

        // When
        boolean result = approvalWorkflowService.checkApprovalRequired(testBill);

        // Then
        assertFalse(result, "Approval should not be required for bill amount below threshold");
    }

    @Test
    void checkApprovalRequired_shouldReturnTrue_whenBillMarkedSensitive() {
        // Given: Bill marked as sensitive regardless of amount
        testBill.setTotalAmount(new BigDecimal("5000000.00")); // Below threshold
        testBill.setIsSensitive(true); // But marked sensitive

        // When
        boolean result = approvalWorkflowService.checkApprovalRequired(testBill);

        // Then
        assertTrue(result, "Approval should be required for bills marked as sensitive");
    }

    @Test
    void checkApprovalRequired_shouldUseDefaultThreshold_whenCompanySettingsNotAvailable() {
        // Given: Company settings not available
        when(companySettingsService.getCurrentCompanySettings()).thenThrow(new RuntimeException("Settings not found"));
        testBill.setTotalAmount(new BigDecimal("25000000.00"));

        // When
        boolean result = approvalWorkflowService.checkApprovalRequired(testBill);

        // Then
        assertTrue(result, "Should use default threshold when settings unavailable");
    }

    // ==================== submitForApproval Tests ====================

    @Test
    void submitForApproval_shouldCreateWorkflowAndUpdateBillStatus() {
        // Given
        when(companySettingsService.getCurrentCompanySettings()).thenReturn(companySettings);
        when(purchaseBillRepository.findById(testBill.getId())).thenReturn(Optional.of(testBill));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(testWorkflow);

        // When
        ApprovalWorkflowDTO result = approvalWorkflowService.submitForApproval(testBill.getId(), CREATOR_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApprovalWorkflowStatus.PENDING);
        assertThat(result.getCreatedById()).isEqualTo(CREATOR_USER_ID);
        assertThat(result.getThresholdAmount()).isEqualByComparingTo(DEFAULT_THRESHOLD);
        assertThat(result.getBillAmount()).isEqualByComparingTo(testBill.getTotalAmount());

        verify(purchaseBillRepository).save(argThat(bill -> bill.getStatus() == PurchaseBillStatus.PENDING_APPROVAL));
        verify(approvalWorkflowRepository).save(any(ApprovalWorkflow.class));
        verify(auditService).logPurchaseBillSubmittedForApproval(
                eq(COMPANY_ID),
                eq(CREATOR_USER_ID),
                eq(testBill.getId()),
                eq(testBill.getTotalAmount()),
                eq(DEFAULT_THRESHOLD));
    }

    @Test
    void submitForApproval_shouldThrowException_whenBillNotFound() {
        // Given
        UUID nonExistentBillId = UUID.randomUUID();
        when(purchaseBillRepository.findById(nonExistentBillId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.submitForApproval(nonExistentBillId, CREATOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Purchase bill not found");
    }

    // ==================== approve Tests ====================

    @Test
    void approve_shouldApproveWorkflowAndPostBill() {
        // Given
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));
        when(purchaseBillRepository.findById(testBill.getId())).thenReturn(Optional.of(testBill));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(testWorkflow);
        when(purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
                eq(COMPANY_ID), eq(testBill.getId())))
                .thenReturn(List.of(createLine(1, new BigDecimal("1000000"), BigDecimal.ZERO, 610L)));
        ChartOfAccount apAccount = new ChartOfAccount();
        apAccount.setId(331L);
        ChartOfAccount vatAccount = new ChartOfAccount();
        vatAccount.setId(3331L);
        when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "331"))
                .thenReturn(Optional.of(apAccount));
        when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
                .thenReturn(Optional.of(vatAccount));
        VoucherDTO voucher = new VoucherDTO();
        voucher.setId(UUID.randomUUID());
        when(voucherService.create(any(VoucherCreateRequest.class))).thenReturn(voucher);

        String approvalReason = "Approved after verification";

        // When
        ApprovalWorkflowDTO result = approvalWorkflowService.approve(
                testWorkflow.getId(), APPROVER_USER_ID, approvalReason);

        // Then
        assertThat(result).isNotNull();
        verify(approvalWorkflowRepository)
                .save(argThat(workflow -> workflow.getStatus() == ApprovalWorkflowStatus.APPROVED &&
                        workflow.getApprovedById().equals(APPROVER_USER_ID) &&
                        workflow.getApprovalReason().equals(approvalReason) &&
                        workflow.getApprovedAt() != null));
        verify(purchaseBillRepository).save(argThat(bill -> bill.getStatus() == PurchaseBillStatus.POSTED &&
                bill.getApprovedById().equals(APPROVER_USER_ID)));
        verify(auditService).logPurchaseBillApproved(
                eq(COMPANY_ID), eq(APPROVER_USER_ID), eq(testBill.getId()), eq(approvalReason));
    }

    @Test
    void approve_shouldCreateVoucherEntriesForBaseAndVatAmounts() {
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));
        when(purchaseBillRepository.findById(testBill.getId())).thenReturn(Optional.of(testBill));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(testWorkflow);
        when(purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
                eq(COMPANY_ID), eq(testBill.getId()))).thenReturn(List.of(
                        createLine(1, new BigDecimal("1000000"), new BigDecimal("100000"), 610L),
                        createLine(2, new BigDecimal("500000"), BigDecimal.ZERO, 620L)));

        ChartOfAccount apAccount = new ChartOfAccount();
        apAccount.setId(331L);
        ChartOfAccount vatAccount = new ChartOfAccount();
        vatAccount.setId(3331L);
        when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "331"))
                .thenReturn(java.util.Optional.of(apAccount));
        when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3331"))
                .thenReturn(java.util.Optional.of(vatAccount));

        VoucherDTO createdVoucher = new VoucherDTO();
        createdVoucher.setId(UUID.randomUUID());
        createdVoucher.setVoucherNumber("VN-001");
        org.mockito.ArgumentCaptor<VoucherCreateRequest> captor = org.mockito.ArgumentCaptor
                .forClass(VoucherCreateRequest.class);
        when(voucherService.create(captor.capture())).thenReturn(createdVoucher);

        approvalWorkflowService.approve(testWorkflow.getId(), APPROVER_USER_ID, "VAT ok");

        VoucherCreateRequest request = captor.getValue();
        assertThat(request.getEntryLines()).hasSize(3);

        VoucherEntryLineRequest expenseLine = request.getEntryLines().get(0);
        assertThat(expenseLine.getDebitAccountId()).isEqualTo(610L);
        assertThat(expenseLine.getCreditAccountId()).isEqualTo(331L);
        assertThat(expenseLine.getAmount()).isEqualByComparingTo("1000000");

        VoucherEntryLineRequest vatLine = request.getEntryLines().get(1);
        assertThat(vatLine.getDebitAccountId()).isEqualTo(3331L);
        assertThat(vatLine.getCreditAccountId()).isEqualTo(331L);
        assertThat(vatLine.getAmount()).isEqualByComparingTo("100000");

        VoucherEntryLineRequest secondExpense = request.getEntryLines().get(2);
        assertThat(secondExpense.getDebitAccountId()).isEqualTo(620L);
        assertThat(secondExpense.getCreditAccountId()).isEqualTo(331L);
        assertThat(secondExpense.getAmount()).isEqualByComparingTo("500000");

        verify(voucherPostingService).postVoucher(createdVoucher.getId(), null);
    }

    @Test
    void approve_shouldThrowException_whenWorkflowNotFound() {
        // Given
        UUID nonExistentWorkflowId = UUID.randomUUID();
        when(approvalWorkflowRepository.findById(nonExistentWorkflowId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.approve(nonExistentWorkflowId, APPROVER_USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approval workflow not found");
    }

    @Test
    void approve_shouldThrowException_whenWorkflowNotPending() {
        // Given: Workflow already approved
        testWorkflow.setStatus(ApprovalWorkflowStatus.APPROVED);
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));

        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.approve(testWorkflow.getId(), APPROVER_USER_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow must be in PENDING status");
    }

    @Test
    void approve_shouldThrowException_whenApproverEqualsCreator() {
        // Given: Approver is same as creator (maker-checker violation)
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));

        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.approve(testWorkflow.getId(), CREATOR_USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approver cannot be the same as creator");
    }

    // ==================== reject Tests ====================

    @Test
    void reject_shouldRejectWorkflowAndReturnBillToDraft() {
        // Given
        testBill.setStatus(PurchaseBillStatus.PENDING_APPROVAL); // Ensure bill is in pending approval state
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));
        when(purchaseBillRepository.findById(testBill.getId())).thenReturn(Optional.of(testBill));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(testWorkflow);
        when(purchaseBillRepository.save(any(PurchaseBill.class))).thenReturn(testBill);
        String rejectionReason = "Missing supporting documents";

        // When
        ApprovalWorkflowDTO result = approvalWorkflowService.reject(
                testWorkflow.getId(), APPROVER_USER_ID, rejectionReason);

        // Then
        assertThat(result).isNotNull();
        verify(approvalWorkflowRepository)
                .save(argThat(workflow -> workflow.getStatus() == ApprovalWorkflowStatus.REJECTED &&
                        workflow.getApprovedById().equals(APPROVER_USER_ID) &&
                        workflow.getRejectionReason().equals(rejectionReason) &&
                        workflow.getRejectedAt() != null));
        org.mockito.ArgumentCaptor<PurchaseBill> billCaptor = org.mockito.ArgumentCaptor.forClass(PurchaseBill.class);
        verify(purchaseBillRepository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getStatus()).isEqualTo(PurchaseBillStatus.REJECTED);
        verify(auditService).logPurchaseBillRejected(
                eq(COMPANY_ID), eq(APPROVER_USER_ID), eq(testBill.getId()), eq(rejectionReason));
    }

    @Test
    void reject_shouldThrowException_whenReasonIsNull() {
        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.reject(testWorkflow.getId(), APPROVER_USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is mandatory");
    }

    @Test
    void reject_shouldThrowException_whenReasonIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.reject(testWorkflow.getId(), APPROVER_USER_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is mandatory");
    }

    @Test
    void reject_shouldThrowException_whenApproverEqualsCreator() {
        // Given: Approver is same as creator
        when(approvalWorkflowRepository.findById(testWorkflow.getId())).thenReturn(Optional.of(testWorkflow));

        // When & Then
        assertThatThrownBy(() -> approvalWorkflowService.reject(
                testWorkflow.getId(), CREATOR_USER_ID, "Some reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approver cannot be the same as creator");
    }

    // ==================== autoApprove Tests ====================

    @Test
    void autoApprove_shouldCreateAutoApprovalWorkflow() {
        // Given: Bill below threshold
        testBill.setTotalAmount(new BigDecimal("15000000.00"));
        when(companySettingsService.getCurrentCompanySettings()).thenReturn(companySettings);
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(testWorkflow);

        // When
        ApprovalWorkflowDTO result = approvalWorkflowService.autoApprove(testBill, CREATOR_USER_ID);

        // Then
        assertThat(result).isNotNull();
        verify(approvalWorkflowRepository)
                .save(argThat(workflow -> workflow.getStatus() == ApprovalWorkflowStatus.AUTO_APPROVED &&
                        workflow.getCreatedById().equals(CREATOR_USER_ID) &&
                        workflow.getApprovedById().equals(CREATOR_USER_ID) &&
                        workflow.getApprovedAt() != null &&
                        workflow.getApprovalReason().contains("Auto-approved")));
        verify(auditService).logPurchaseBillAutoApproved(
                eq(COMPANY_ID),
                eq(CREATOR_USER_ID),
                eq(testBill.getId()),
                any(BigDecimal.class),
                any(BigDecimal.class));
    }

    // ==================== getPendingApprovals Tests ====================

    @Test
    void getPendingApprovals_shouldReturnPendingWorkflowsForCompany() {
        // Given
        ApprovalWorkflow workflow1 = createTestWorkflow(UUID.randomUUID(), "BILL-001");
        ApprovalWorkflow workflow2 = createTestWorkflow(UUID.randomUUID(), "BILL-002");
        when(approvalWorkflowRepository.findByStatus(ApprovalWorkflowStatus.PENDING))
                .thenReturn(List.of(workflow1, workflow2));
        when(purchaseBillRepository.findById(any())).thenReturn(Optional.of(testBill));

        // When
        List<ApprovalWorkflowDTO> results = approvalWorkflowService.getPendingApprovals();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(dto -> dto.getStatus() == ApprovalWorkflowStatus.PENDING);
        verify(approvalWorkflowRepository).findByStatus(ApprovalWorkflowStatus.PENDING);
    }

    // ==================== getApprovalHistory Tests ====================

    @Test
    void getApprovalHistory_shouldReturnWorkflowHistoryForBill() {
        // Given
        ApprovalWorkflow pendingWorkflow = createTestWorkflow(testBill.getId(), "BILL-001");
        pendingWorkflow.setStatus(ApprovalWorkflowStatus.PENDING);

        ApprovalWorkflow approvedWorkflow = createTestWorkflow(testBill.getId(), "BILL-001");
        approvedWorkflow.setStatus(ApprovalWorkflowStatus.APPROVED);

        when(approvalWorkflowRepository.findByPurchaseBillId(testBill.getId()))
                .thenReturn(List.of(pendingWorkflow, approvedWorkflow));
        when(purchaseBillRepository.findById(testBill.getId())).thenReturn(Optional.of(testBill));

        // When
        List<ApprovalWorkflowDTO> results = approvalWorkflowService.getApprovalHistory(testBill.getId());

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting("status").containsExactlyInAnyOrder(ApprovalWorkflowStatus.PENDING,
                ApprovalWorkflowStatus.APPROVED);
        verify(approvalWorkflowRepository).findByPurchaseBillId(testBill.getId());
    }

    // ==================== getPendingApprovalsCount Tests ====================

    @Test
    void getPendingApprovalsCount_shouldReturnCorrectCount() {
        // Given
        when(approvalWorkflowRepository.countPending()).thenReturn(5L);

        // When
        long count = approvalWorkflowService.getPendingApprovalsCount();

        // Then
        assertThat(count).isEqualTo(5L);
        verify(approvalWorkflowRepository).countPending();
    }

    // ==================== Helper Methods ====================

    private ApprovalWorkflow createTestWorkflow(UUID billId, String billNumber) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(UUID.randomUUID());
        workflow.setCompanyId(COMPANY_ID);
        workflow.setPurchaseBillId(billId);
        workflow.setCreatedById(CREATOR_USER_ID);
        workflow.setStatus(ApprovalWorkflowStatus.PENDING);
        workflow.setThresholdAmount(DEFAULT_THRESHOLD);
        workflow.setBillAmount(new BigDecimal("25000000.00"));
        workflow.setIsSensitive(false);
        workflow.setCreatedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());
        return workflow;
    }

    private PurchaseBillLine createLine(int lineNumber, BigDecimal amount, BigDecimal vatAmount, Long accountId) {
        PurchaseBillLine line = new PurchaseBillLine();
        line.setId(UUID.randomUUID());
        line.setCompanyId(COMPANY_ID);
        line.setPurchaseBillId(testBill.getId());
        line.setLineNumber(lineNumber);
        line.setAccountId(accountId);
        line.setAmount(amount);
        line.setVatAmount(vatAmount);
        line.setVatRate(vatAmount.compareTo(BigDecimal.ZERO) > 0 ? VatRate.TEN : VatRate.ZERO);
        line.setDescription("Line " + lineNumber);
        return line;
    }
}
