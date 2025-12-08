package com.accounting.controller.sales;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.*;
import com.accounting.repository.*;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for Sales Invoice approval workflow endpoints.
 * Tests the full HTTP→Controller→Service→Repository flow for AR approval.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SalesInvoiceApprovalIntegrationTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;

    @Autowired
    private SalesInvoiceLineRepository salesInvoiceLineRepository;

    @Autowired
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanySettingsRepository companySettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;
    private CompanySettings testSettings;
    private User makerUser;
    private User approverUser;
    private Customer testCustomer;
    private ChartOfAccount revenueAccount;
    private ChartOfAccount arAccount;
    private ChartOfAccount vatAccount;
    private AccountingPeriod openPeriod;
    private String makerToken;
    private String approverToken;

    @BeforeEach
    void setUp() {
        // Create test company
        testCompany = new Company();
        testCompany.setCode("TEST");
        testCompany.setName("Test Company");
        testCompany.setTaxCode("1234567890");
        testCompany.setAddress("Test Address");
        testCompany = companyRepository.save(testCompany);

        CompanyContext.setCompanyId(testCompany.getId());

        // Create company settings with 100M VND threshold
        testSettings = new CompanySettings();
        testSettings.setCompanyId(testCompany.getId());
        testSettings.setSalesInvoiceApprovalThresholdAmount(new BigDecimal("100000000")); // 100M VND
        testSettings.setCreatedAt(Instant.now());
        testSettings.setUpdatedAt(Instant.now());
        testSettings = companySettingsRepository.save(testSettings);

        // Create maker user (accountant)
        makerUser = new User();
        makerUser.setEmail("maker@test.com");
        makerUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        makerUser.setFullName("Maker User");
        makerUser.setRole("accountant");
        makerUser.setStatus("ACTIVE");
        makerUser.setCompanyId(testCompany.getId());
        makerUser.setCreatedAt(Instant.now());
        makerUser.setUpdatedAt(Instant.now());
        makerUser = userRepository.save(makerUser);
        makerToken = jwtTokenProvider.generateAccessToken(makerUser.getId(), makerUser.getEmail(), makerUser.getRole());

        // Create approver user (chief_accountant)
        approverUser = new User();
        approverUser.setEmail("approver@test.com");
        approverUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        approverUser.setFullName("Approver User");
        approverUser.setRole("chief_accountant");
        approverUser.setStatus("ACTIVE");
        approverUser.setCompanyId(testCompany.getId());
        approverUser.setCreatedAt(Instant.now());
        approverUser.setUpdatedAt(Instant.now());
        approverUser = userRepository.save(approverUser);
        approverToken = jwtTokenProvider.generateAccessToken(approverUser.getId(), approverUser.getEmail(),
                approverUser.getRole());

        // Create test customer
        testCustomer = new Customer();
        testCustomer.setCode("CUST-001");
        testCustomer.setName("Test Customer");
        testCustomer.setTaxCode("9876543210");
        testCustomer.setCompanyId(testCompany.getId());
        testCustomer.setCreatedAt(Instant.now());
        testCustomer.setUpdatedAt(Instant.now());
        testCustomer = customerRepository.save(testCustomer);

        // Create chart of accounts
        // AR Account (131)
        arAccount = new ChartOfAccount();
        arAccount.setCode("131");
        arAccount.setName("Accounts Receivable");
        arAccount.setType("Asset");
        arAccount.setNormalSide("Debit");
        arAccount.setOrderingPosition(1);
        arAccount.setPostable(true);
        arAccount.setCompanyId(testCompany.getId());
        arAccount = chartOfAccountsRepository.save(arAccount);

        // Revenue Account (511)
        revenueAccount = new ChartOfAccount();
        revenueAccount.setCode("511");
        revenueAccount.setName("Sales Revenue");
        revenueAccount.setType("Revenue");
        revenueAccount.setNormalSide("Credit");
        revenueAccount.setOrderingPosition(2);
        revenueAccount.setPostable(true);
        revenueAccount.setCompanyId(testCompany.getId());
        revenueAccount = chartOfAccountsRepository.save(revenueAccount);

        // VAT Account (3332)
        vatAccount = new ChartOfAccount();
        vatAccount.setCode("3332");
        vatAccount.setName("Output VAT");
        vatAccount.setType("Liability");
        vatAccount.setNormalSide("Credit");
        vatAccount.setOrderingPosition(3);
        vatAccount.setPostable(true);
        vatAccount.setCompanyId(testCompany.getId());
        vatAccount = chartOfAccountsRepository.save(vatAccount);

        // Create open accounting period
        openPeriod = new AccountingPeriod();
        openPeriod.setCompanyId(testCompany.getId());
        openPeriod.setPeriodName("2025-11");
        openPeriod.setStartDate(LocalDate.of(2025, 11, 1));
        openPeriod.setEndDate(LocalDate.of(2025, 11, 30));
        openPeriod.setStatus(PeriodStatus.OPEN);
        openPeriod.setCreatedAt(Instant.now());
        openPeriod.setUpdatedAt(Instant.now());
        openPeriod = accountingPeriodRepository.save(openPeriod);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ==================== Submit for Approval Tests ====================

    @Test
    void submitForApproval_aboveThreshold_createsWorkflow() throws Exception {
        // Given: Invoice above 100M threshold
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.DRAFT);

        // When: Submit for approval
        mockMvc.perform(post("/api/v1/ar/sales-invoices/{id}/submit-for-approval", invoice.getId())
                .header("Authorization", "Bearer " + makerToken)
                .contentType(APPLICATION_JSON))
                // Then: Returns workflow with PENDING status
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.salesInvoiceId").value(invoice.getId().toString()))
                .andExpect(jsonPath("$.thresholdAmount").value(100000000));

        // Verify invoice status changed
        SalesInvoice updated = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assert updated.getStatus() == SalesInvoiceStatus.PENDING_APPROVAL;
    }

    @Test
    void submitForApproval_belowThreshold_autoApproves() throws Exception {
        // Given: Invoice below 100M threshold
        SalesInvoice invoice = createTestInvoice(new BigDecimal("50000000"), SalesInvoiceStatus.DRAFT);

        // When: Submit for approval
        mockMvc.perform(post("/api/v1/ar/sales-invoices/{id}/submit-for-approval", invoice.getId())
                .header("Authorization", "Bearer " + makerToken)
                .contentType(APPLICATION_JSON))
                // Then: Auto-approves and posts
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTO_APPROVED"));

        // Verify invoice status changed to POSTED
        SalesInvoice updated = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assert updated.getStatus() == SalesInvoiceStatus.POSTED;
        assert updated.getPostedVoucherId() != null;
    }

    // ==================== Approve Tests ====================

    @Test
    void approve_asApprover_succeeds() throws Exception {
        // Given: Invoice in PENDING_APPROVAL with workflow
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        ApprovalWorkflow workflow = createTestWorkflow(invoice, makerUser.getId());

        // When: Approver approves with reason
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("reason", "Looks good"));

        mockMvc.perform(post("/api/v1/ar/sales-invoices/workflows/{workflowId}/approve", workflow.getId())
                .header("Authorization", "Bearer " + approverToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
                // Then: Returns approved workflow
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify invoice status changed to POSTED
        SalesInvoice updated = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assert updated.getStatus() == SalesInvoiceStatus.POSTED;
        assert updated.getApprovedById().equals(approverUser.getId());
        assert updated.getPostedVoucherId() != null;
    }

    @Test
    void approve_asMaker_forbidden() throws Exception {
        // Given: Invoice created by maker
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        ApprovalWorkflow workflow = createTestWorkflow(invoice, makerUser.getId());

        // When: Maker tries to approve their own invoice
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("reason", "Self approval"));

        mockMvc.perform(post("/api/v1/ar/sales-invoices/workflows/{workflowId}/approve", workflow.getId())
                .header("Authorization", "Bearer " + makerToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
                // Then: Returns 403 Forbidden
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Cannot approve your own invoice")));
    }

    // ==================== Reject Tests ====================

    @Test
    void reject_withReason_revertsToRejected() throws Exception {
        // Given: Invoice in PENDING_APPROVAL
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        ApprovalWorkflow workflow = createTestWorkflow(invoice, makerUser.getId());

        // When: Approver rejects with reason
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("reason", "VAT calculation incorrect"));

        mockMvc.perform(post("/api/v1/ar/sales-invoices/workflows/{workflowId}/reject", workflow.getId())
                .header("Authorization", "Bearer " + approverToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
                // Then: Returns rejected workflow
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify invoice status changed to REJECTED
        SalesInvoice updated = salesInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assert updated.getStatus() == SalesInvoiceStatus.REJECTED;
    }

    @Test
    void reject_withoutReason_badRequest() throws Exception {
        // Given: Invoice in PENDING_APPROVAL
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        ApprovalWorkflow workflow = createTestWorkflow(invoice, makerUser.getId());

        // When: Reject without reason
        mockMvc.perform(post("/api/v1/ar/sales-invoices/workflows/{workflowId}/reject", workflow.getId())
                .header("Authorization", "Bearer " + approverToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
                // Then: Returns 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    // ==================== Query Methods Tests ====================

    @Test
    void getPendingApprovals_returnsOnlyPending() throws Exception {
        // Given: Multiple workflows in different states
        SalesInvoice pending1 = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        createTestWorkflow(pending1, makerUser.getId());

        SalesInvoice pending2 = createTestInvoice(new BigDecimal("120000000"), SalesInvoiceStatus.PENDING_APPROVAL);
        createTestWorkflow(pending2, makerUser.getId());

        // When: Get pending approvals
        mockMvc.perform(get("/api/v1/ar/sales-invoices/workflows/pending")
                .header("Authorization", "Bearer " + approverToken))
                // Then: Returns only pending workflows
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    @Test
    void getApprovalHistory_returnsAllWorkflows() throws Exception {
        // Given: Invoice with approval history
        SalesInvoice invoice = createTestInvoice(new BigDecimal("150000000"), SalesInvoiceStatus.POSTED);
        ApprovalWorkflow workflow = createTestWorkflow(invoice, makerUser.getId());
        workflow.setStatus(ApprovalWorkflowStatus.APPROVED);
        approvalWorkflowRepository.save(workflow);

        // When: Get approval history
        mockMvc.perform(get("/api/v1/ar/sales-invoices/{id}/workflows", invoice.getId())
                .header("Authorization", "Bearer " + makerToken))
                // Then: Returns workflow history
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].salesInvoiceId").value(invoice.getId().toString()));
    }

    // ==================== Helper Methods ====================

    private SalesInvoice createTestInvoice(BigDecimal totalAmount, SalesInvoiceStatus status) {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setCompanyId(testCompany.getId());
        invoice.setCustomerId(testCustomer.getId());
        invoice.setInvoiceNumber("SI-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setReference("Test Reference");
        invoice.setStatus(status);
        invoice.setTotalAmount(totalAmount);
        invoice.setVatAmount(totalAmount.multiply(new BigDecimal("0.1")));
        invoice.setCreatedById(makerUser.getId());
        invoice.setCreatedAt(Instant.now());
        invoice.setUpdatedAt(Instant.now());
        invoice = salesInvoiceRepository.save(invoice);

        // Create invoice line
        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setSalesInvoiceId(invoice.getId());
        line.setCompanyId(testCompany.getId());
        line.setLineNumber(1);
        line.setDescription("Test Item");
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(totalAmount.divide(new BigDecimal("1.1"), 2, java.math.RoundingMode.HALF_UP));
        line.setAmount(line.getUnitPrice());
        line.setVatRate(VatRate.TEN);
        line.setVatAmount(totalAmount.subtract(line.getAmount()));
        line.setAccountId(revenueAccount.getId());
        line.setCreatedAt(Instant.now());
        line.setUpdatedAt(Instant.now());
        salesInvoiceLineRepository.save(line);

        return invoice;
    }

    private ApprovalWorkflow createTestWorkflow(SalesInvoice invoice, Long createdById) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setCompanyId(testCompany.getId());
        workflow.setSalesInvoiceId(invoice.getId());
        workflow.setStatus(ApprovalWorkflowStatus.PENDING);
        workflow.setCreatedById(createdById);
        workflow.setThresholdAmount(testSettings.getSalesInvoiceApprovalThresholdAmount());
        workflow.setBillAmount(invoice.getTotalAmount());
        workflow.setIsSensitive(false);
        workflow.setCreatedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());
        return approvalWorkflowRepository.save(workflow);
    }
}
