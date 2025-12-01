package com.accounting.service.impl.sales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.dto.CompanySettingsDto;
import com.accounting.entity.*;
import com.accounting.repository.ApprovalWorkflowRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesInvoiceApprovalServiceImplTest {

        @Mock
        private ApprovalWorkflowRepository approvalWorkflowRepository;
        @Mock
        private SalesInvoiceRepository salesInvoiceRepository;
        @Mock
        private CompanySettingsService companySettingsService;
        @Mock
        private PeriodManagementService periodManagementService;
        @Mock
        private AuditService auditService;
        @Mock
        private com.accounting.repository.SalesInvoiceLineRepository salesInvoiceLineRepository;
        @Mock
        private com.accounting.repository.ChartOfAccountsRepository chartOfAccountsRepository;
        @Mock
        private VoucherService voucherService;
        @Mock
        private com.accounting.service.voucher.VoucherPostingService voucherPostingService;
        @Mock
        private VATService vatService;
        @Mock
        private ARVATService arVatService;
        @Mock
        private ARAgingService arAgingService;

        private List<com.accounting.entity.SalesInvoiceLine> createMockInvoiceLines(UUID invoiceId) {
                com.accounting.entity.SalesInvoiceLine line = new com.accounting.entity.SalesInvoiceLine();
                line.setSalesInvoiceId(invoiceId);
                line.setLineNumber(1);
                line.setDescription("Test Item");
                line.setQuantity(BigDecimal.ONE);
                line.setUnitPrice(BigDecimal.TEN);
                line.setAmount(BigDecimal.TEN);
                line.setAccountId(1L);
                return List.of(line);
        }

        private ChartOfAccount createMockAccount(Long id, String code, String name) {
                ChartOfAccount account = new ChartOfAccount();
                account.setId(id);
                account.setCode(code);
                account.setName(name);
                account.setCompanyId(COMPANY_ID);
                return account;
        }

        private SalesInvoiceApprovalServiceImpl approvalService;

        private static final Long COMPANY_ID = 1L;
        private static final Long CREATOR_ID = 100L;
        private static final Long APPROVER_ID = 200L;
        private static final UUID INVOICE_ID = UUID.randomUUID();
        private static final UUID WORKFLOW_ID = UUID.randomUUID();
        private static final BigDecimal THRESHOLD = new BigDecimal("100000000"); // 100M VND
        private static final BigDecimal BELOW_THRESHOLD = new BigDecimal("50000000"); // 50M VND
        private static final BigDecimal ABOVE_THRESHOLD = new BigDecimal("150000000"); // 150M VND

        @BeforeEach
        void setUp() {
                approvalService = new SalesInvoiceApprovalServiceImpl(
                                approvalWorkflowRepository,
                                salesInvoiceRepository,
                                companySettingsService,
                                periodManagementService,
                                auditService,
                                salesInvoiceLineRepository,
                                chartOfAccountsRepository,
                                voucherService,
                                voucherPostingService,
                                vatService,
                                arVatService,
                                arAgingService);

                CompanyContext.setCompanyId(COMPANY_ID);

                // Mock company settings with default threshold
                CompanySettingsDto settings = new CompanySettingsDto();
                settings.setSalesInvoiceApprovalThresholdAmount(THRESHOLD);
                when(companySettingsService.getCurrentCompanySettings()).thenReturn(settings);

                // Mock period management to return open period by default
                com.accounting.dto.AccountingPeriodDTO openPeriod = new com.accounting.dto.AccountingPeriodDTO();
                openPeriod.setId(UUID.randomUUID());
                openPeriod.setStatus(PeriodStatus.OPEN);
                when(periodManagementService.findPeriodByDate(any(LocalDate.class)))
                                .thenReturn(Optional.of(openPeriod));
                when(periodManagementService.isPeriodOpen(any(UUID.class))).thenReturn(true);
                when(periodManagementService.isDateInOpenPeriod(any(LocalDate.class))).thenReturn(true);

                // Mock invoice lines repository
                when(salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(any(UUID.class)))
                                .thenAnswer(invocation -> createMockInvoiceLines(invocation.getArgument(0)));
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        // ==================== Threshold Logic Tests ====================

        @Test
        void submitForApproval_belowThreshold_doesNotRequireApproval() {
                // Arrange: Invoice below threshold doesn't require approval - service rejects
                // it
                SalesInvoice invoice = createInvoice(BELOW_THRESHOLD, SalesInvoiceStatus.DRAFT);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(vatService.validateVATSum(invoice))
                                .thenReturn(new com.accounting.dto.VATValidationResultDTO(true));

                // Act & Assert: Service throws IllegalArgumentException for below-threshold
                // invoices
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> approvalService.submitForApproval(INVOICE_ID, CREATOR_ID));

                assertTrue(exception.getMessage().contains("does not require approval"));
        }

        @Test
        void submitForApproval_aboveThreshold_requiresApproval() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.DRAFT);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(invoice);
                com.accounting.dto.VATValidationResultDTO vatResult = new com.accounting.dto.VATValidationResultDTO();
                vatResult.setValid(true);
                when(vatService.validateVATSum(invoice)).thenReturn(vatResult);
                when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class)))
                                .thenAnswer(invocation -> {
                                        ApprovalWorkflow wf = invocation.getArgument(0);
                                        wf.setId(WORKFLOW_ID);
                                        return wf;
                                });
                when(voucherService.create(any())).thenReturn(new com.accounting.dto.VoucherDTO());
                // Mock chart of accounts for voucher creation
                when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
                                .thenReturn(Optional.of(createMockAccount(1L, "131", "AR")));
                when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3332"))
                                .thenReturn(Optional.of(createMockAccount(2L, "3332", "VAT")));

                // Act
                ApprovalWorkflowDTO result = approvalService.submitForApproval(INVOICE_ID, CREATOR_ID);

                // Assert
                assertEquals(ApprovalWorkflowStatus.PENDING, result.getStatus());
                assertEquals(SalesInvoiceStatus.PENDING_APPROVAL, invoice.getStatus());
                verify(auditService)
                                .logSalesInvoiceSubmittedForApproval(eq(COMPANY_ID), eq(CREATOR_ID), eq(INVOICE_ID),
                                                any(BigDecimal.class), any(BigDecimal.class));
        }

        // ==================== Maker-Checker Enforcement Tests ====================

        @Test
        void approve_sameUserAsCreator_throwsForbidden() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));

                // Act & Assert
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> approvalService.approve(WORKFLOW_ID, CREATOR_ID, "Approved"));

                assertTrue(exception.getMessage().contains("Approver cannot be the same as creator"));
                // Approval attempt logging is done internally by the service - no need to
                // verify
        }

        @Test
        void approve_differentUser_succeeds() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
                when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(workflow);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(invoice);
                com.accounting.dto.VATValidationResultDTO vatResult = new com.accounting.dto.VATValidationResultDTO();
                vatResult.setValid(true);
                when(vatService.validateVATSum(invoice)).thenReturn(vatResult);
                when(voucherService.create(any())).thenReturn(new com.accounting.dto.VoucherDTO());
                // Mock chart of accounts for voucher creation
                when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "131"))
                                .thenReturn(Optional.of(createMockAccount(1L, "131", "AR")));
                when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "3332"))
                                .thenReturn(Optional.of(createMockAccount(2L, "3332", "VAT")));

                // Act
                ApprovalWorkflowDTO result = approvalService.approve(WORKFLOW_ID, APPROVER_ID, "Approved");

                // Assert
                assertEquals(ApprovalWorkflowStatus.APPROVED, result.getStatus());
                assertEquals(SalesInvoiceStatus.POSTED, invoice.getStatus());
                assertEquals(APPROVER_ID, invoice.getApprovedById());
                verify(auditService)
                                .logSalesInvoiceApproved(eq(COMPANY_ID), eq(APPROVER_ID), eq(INVOICE_ID),
                                                eq("Approved"));
        }

        // ==================== Period Closed Blocking Tests ====================

        @Test
        void approve_periodClosed_throwsBadRequest() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(vatService.validateVATSum(invoice))
                                .thenReturn(new com.accounting.dto.VATValidationResultDTO(true));

                com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
                closedPeriod.setId(UUID.randomUUID());
                closedPeriod.setStatus(PeriodStatus.CLOSED);
                closedPeriod.setPeriodName("2024-12");
                when(periodManagementService.findPeriodByDate(any(LocalDate.class)))
                                .thenReturn(Optional.of(closedPeriod));
                when(periodManagementService.isDateInOpenPeriod(any(LocalDate.class))).thenReturn(false);

                // Act & Assert
                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.approve(WORKFLOW_ID, APPROVER_ID, "Approved"));

                assertTrue(exception.getMessage().contains("Period") || exception.getMessage().contains("closed"));
        }

        @Test
        void submitForApproval_periodClosed_throwsBadRequest() {
                // Arrange: Use above threshold so approval is required, but period is closed
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.DRAFT);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(vatService.validateVATSum(invoice))
                                .thenReturn(new com.accounting.dto.VATValidationResultDTO(true));

                // Override the default open period mock to return closed period
                com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
                closedPeriod.setId(UUID.randomUUID());
                closedPeriod.setStatus(PeriodStatus.CLOSED);
                closedPeriod.setPeriodName("2025-11");
                when(periodManagementService.findPeriodByDate(any(LocalDate.class)))
                                .thenReturn(Optional.of(closedPeriod));
                when(periodManagementService.isDateInOpenPeriod(any(LocalDate.class))).thenReturn(false);

                // Act & Assert
                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.submitForApproval(INVOICE_ID, CREATOR_ID));

                assertTrue(exception.getMessage().contains("closed") || exception.getMessage().contains("DRAFT"));
        }

        // ==================== Rejection Tests ====================

        @Test
        void reject_withReason_revertsToDraft() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
                when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenReturn(workflow);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenReturn(invoice);

                String rejectionReason = "VAT calculation incorrect";

                // Act
                ApprovalWorkflowDTO result = approvalService.reject(WORKFLOW_ID, APPROVER_ID, rejectionReason);

                // Assert
                assertEquals(ApprovalWorkflowStatus.REJECTED, result.getStatus());
                // Note: Service sets invoice status to DRAFT on rejection (per implementation)
                assertEquals(SalesInvoiceStatus.DRAFT, invoice.getStatus());
                assertEquals(rejectionReason, workflow.getRejectionReason());
                verify(auditService)
                                .logSalesInvoiceRejected(
                                                eq(COMPANY_ID), eq(APPROVER_ID), eq(INVOICE_ID), eq(rejectionReason));
                verify(voucherService, never()).postSalesInvoiceVoucher(any(), anyLong());
        }

        @Test
        void reject_withoutReason_throwsBadRequest() {
                // Arrange
                ApprovalWorkflow workflow = createWorkflow(
                                createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL),
                                CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));

                // Act & Assert
                assertThrows(
                                IllegalArgumentException.class,
                                () -> approvalService.reject(WORKFLOW_ID, APPROVER_ID, null));
                assertThrows(
                                IllegalArgumentException.class,
                                () -> approvalService.reject(WORKFLOW_ID, APPROVER_ID, ""));
                assertThrows(
                                IllegalArgumentException.class,
                                () -> approvalService.reject(WORKFLOW_ID, APPROVER_ID, "   "));
        }

        // ==================== Invalid Status Transition Tests ====================

        @Test
        void submitForApproval_alreadyPendingApproval_throwsBadRequest() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.submitForApproval(INVOICE_ID, CREATOR_ID));

                assertTrue(exception.getMessage().contains("DRAFT") || exception.getMessage().contains("status"));
        }

        @Test
        void submitForApproval_alreadyPosted_throwsBadRequest() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.POSTED);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));

                // Act & Assert
                assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.submitForApproval(INVOICE_ID, CREATOR_ID));
        }

        @Test
        void approve_workflowNotPending_throwsBadRequest() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.POSTED);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                workflow.setStatus(ApprovalWorkflowStatus.APPROVED);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));

                // Act & Assert
                assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.approve(WORKFLOW_ID, APPROVER_ID, "Approved"));
        }

        // ==================== VAT Validation Tests ====================

        @Test
        void submitForApproval_invalidVAT_throwsBadRequest() {
                // Arrange: Use above threshold so approval is required, but VAT is invalid
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.DRAFT);
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                // VAT validation returns invalid - should throw before creating workflow
                com.accounting.dto.VATValidationResultDTO vatResult = new com.accounting.dto.VATValidationResultDTO();
                vatResult.setValid(false);
                vatResult.setErrors(List.of("VAT validation failed"));
                when(vatService.validateVATSum(invoice)).thenReturn(vatResult);

                // Act & Assert
                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.submitForApproval(INVOICE_ID, CREATOR_ID));

                assertTrue(exception.getMessage().contains("VAT") || exception.getMessage().contains("validation"));
                verify(voucherService, never()).postSalesInvoiceVoucher(any(), anyLong());
        }

        @Test
        void approve_invalidVAT_throwsBadRequest() {
                // Arrange
                SalesInvoice invoice = createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL);
                ApprovalWorkflow workflow = createWorkflow(invoice, CREATOR_ID);
                when(approvalWorkflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
                when(salesInvoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
                com.accounting.dto.VATValidationResultDTO vatResult = new com.accounting.dto.VATValidationResultDTO();
                vatResult.setValid(false);
                when(vatService.validateVATSum(invoice)).thenReturn(vatResult);

                // Act & Assert
                IllegalStateException exception = assertThrows(
                                IllegalStateException.class,
                                () -> approvalService.approve(WORKFLOW_ID, APPROVER_ID, "Approved"));

                assertTrue(exception.getMessage().contains("VAT") || exception.getMessage().contains("validation"));
                verify(voucherService, never()).postSalesInvoiceVoucher(any(), anyLong());
        }

        // ==================== Query Methods Tests ====================

        @Test
        void getPendingApprovals_returnsOnlyPendingWorkflows() {
                // Arrange
                ApprovalWorkflow pending1 = createWorkflow(
                                createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL),
                                CREATOR_ID);
                ApprovalWorkflow pending2 = createWorkflow(
                                createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.PENDING_APPROVAL),
                                CREATOR_ID);
                when(approvalWorkflowRepository.findByCompanyIdAndStatusAndSalesInvoiceIdIsNotNull(
                                COMPANY_ID, ApprovalWorkflowStatus.PENDING))
                                .thenReturn(List.of(pending1, pending2));

                // Act
                List<ApprovalWorkflowDTO> result = approvalService.getPendingApprovals();

                // Assert
                assertEquals(2, result.size());
                assertTrue(result.stream().allMatch(w -> w.getStatus() == ApprovalWorkflowStatus.PENDING));
        }

        @Test
        void getApprovalHistory_returnsAllWorkflowsForInvoice() {
                // Arrange
                ApprovalWorkflow workflow1 = createWorkflow(createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.POSTED),
                                CREATOR_ID);
                workflow1.setStatus(ApprovalWorkflowStatus.APPROVED);
                ApprovalWorkflow workflow2 = createWorkflow(createInvoice(ABOVE_THRESHOLD, SalesInvoiceStatus.REJECTED),
                                CREATOR_ID);
                workflow2.setStatus(ApprovalWorkflowStatus.REJECTED);

                when(approvalWorkflowRepository.findBySalesInvoiceId(INVOICE_ID))
                                .thenReturn(List.of(workflow1, workflow2));

                // Act
                List<ApprovalWorkflowDTO> result = approvalService.getApprovalHistory(INVOICE_ID);

                // Assert
                assertEquals(2, result.size());
        }

        // ==================== Helper Methods ====================

        private SalesInvoice createInvoice(BigDecimal totalAmount, SalesInvoiceStatus status) {
                SalesInvoice invoice = new SalesInvoice();
                invoice.setId(INVOICE_ID);
                invoice.setCompanyId(COMPANY_ID);
                invoice.setCustomerId(1L);
                invoice.setInvoiceNumber("SI-2024-001");
                invoice.setInvoiceDate(LocalDate.now());
                invoice.setDueDate(LocalDate.now().plusDays(30));
                invoice.setReference("Test Reference");
                invoice.setStatus(status);
                invoice.setTotalAmount(totalAmount);
                invoice.setVatAmount(totalAmount.multiply(new BigDecimal("0.1")));
                invoice.setCreatedById(CREATOR_ID);
                invoice.setCreatedAt(Instant.now());
                invoice.setUpdatedAt(Instant.now());
                // SalesInvoice doesn't have sensitive field - it's only on ApprovalWorkflow
                return invoice;
        }

        private ApprovalWorkflow createWorkflow(SalesInvoice invoice, Long submitterId) {
                ApprovalWorkflow workflow = new ApprovalWorkflow();
                workflow.setId(WORKFLOW_ID);
                workflow.setCompanyId(COMPANY_ID);
                workflow.setSalesInvoiceId(invoice.getId());
                workflow.setStatus(ApprovalWorkflowStatus.PENDING);
                workflow.setCreatedById(submitterId);
                workflow.setBillAmount(invoice.getTotalAmount());
                workflow.setThresholdAmount(THRESHOLD);
                workflow.setIsSensitive(false);
                workflow.setCreatedAt(Instant.now());
                workflow.setUpdatedAt(Instant.now());
                return workflow;
        }
}
