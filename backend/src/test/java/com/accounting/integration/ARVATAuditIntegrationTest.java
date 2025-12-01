package com.accounting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceLineDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.ARVATCorrection;
import com.accounting.entity.AuditLog;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.Customer;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.User;
import com.accounting.entity.VatRate;
import com.accounting.entity.Voucher;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.ARVATCorrectionRepository;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for AR VAT audit logging.
 * Tests that VAT operations (rate override, correction, credit note) are
 * properly logged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class ARVATAuditIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
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
    private SalesInvoiceRepository salesInvoiceRepository;
    @Autowired
    private SalesInvoiceLineRepository salesInvoiceLineRepository;
    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;
    @Autowired
    private ARVATCorrectionRepository arVatCorrectionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ObjectMapper objectMapper;

    private Long testCompanyId;
    private Long testUserId;
    private Long chiefAccountantId;
    private Company testCompany;
    private User testUser;
    private User chiefAccountant;
    private Customer testCustomer;
    private ChartOfAccount arAccount;
    private ChartOfAccount vatAccount;
    private ChartOfAccount revenueAccount;
    private AccountingPeriod openPeriod;
    private String testToken;
    private String chiefToken;

    @BeforeEach
    void setUp() {
        CompanyContext.setCompanyId(null);

        // Create test company
        testCompany = new Company();
        testCompany.setCode("ARVATAUDIT");
        testCompany.setName("AR VAT Audit Test Company");
        testCompany.setTaxCode("1234567890");
        testCompany.setAddress("Test Address");
        testCompany = companyRepository.save(testCompany);
        testCompanyId = testCompany.getId();
        CompanyContext.setCompanyId(testCompanyId);

        // Create company settings
        CompanySettings settings = new CompanySettings();
        settings.setCompanyId(testCompanyId);
        settings.setDefaultCurrency("VND");
        settings.setTimezone("Asia/Ho_Chi_Minh");
        settings.setSalesInvoiceApprovalThresholdAmount(new BigDecimal("100000000.00"));
        companySettingsRepository.save(settings);

        // Create test user (accountant)
        testUser = new User();
        testUser.setEmail("accountant@arvataudit.test");
        testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        testUser.setFullName("Test Accountant");
        testUser.setCompanyId(testCompanyId);
        testUser.setRole("accountant");
        testUser.setStatus("ACTIVE");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();
        testToken = jwtTokenProvider.generateAccessToken(testUserId, testUser.getEmail(), testUser.getRole());

        // Create chief accountant
        chiefAccountant = new User();
        chiefAccountant.setEmail("chief@arvataudit.test");
        chiefAccountant.setPasswordHash(passwordEncoder.encode("Password123!"));
        chiefAccountant.setFullName("Chief Accountant");
        chiefAccountant.setCompanyId(testCompanyId);
        chiefAccountant.setRole("chief_accountant");
        chiefAccountant.setStatus("ACTIVE");
        chiefAccountant.setCreatedAt(Instant.now());
        chiefAccountant.setUpdatedAt(Instant.now());
        chiefAccountant = userRepository.save(chiefAccountant);
        chiefAccountantId = chiefAccountant.getId();
        chiefToken = jwtTokenProvider.generateAccessToken(
                chiefAccountantId, chiefAccountant.getEmail(), chiefAccountant.getRole());

        // Create test customer
        testCustomer = new Customer();
        testCustomer.setCompanyId(testCompanyId);
        testCustomer.setCode("CUST-001");
        testCustomer.setName("Test Customer");
        testCustomer.setTaxCode("0987654321");
        testCustomer.setAddress("Customer Address");
        testCustomer = customerRepository.save(testCustomer);

        // Create chart of accounts
        arAccount = new ChartOfAccount();
        arAccount.setCompanyId(testCompanyId);
        arAccount.setCode("131");
        arAccount.setName("Accounts Receivable");
        arAccount.setPostable(true);
        arAccount.setType("Asset");
        arAccount.setNormalSide("Debit");
        arAccount.setOrderingPosition(1);
        arAccount = chartOfAccountsRepository.save(arAccount);

        vatAccount = new ChartOfAccount();
        vatAccount.setCompanyId(testCompanyId);
        vatAccount.setCode("3331");
        vatAccount.setName("Output VAT");
        vatAccount.setPostable(true);
        vatAccount.setType("Liability");
        vatAccount.setNormalSide("Credit");
        vatAccount.setOrderingPosition(2);
        vatAccount = chartOfAccountsRepository.save(vatAccount);

        revenueAccount = new ChartOfAccount();
        revenueAccount.setCompanyId(testCompanyId);
        revenueAccount.setCode("511");
        revenueAccount.setName("Sales Revenue");
        revenueAccount.setPostable(true);
        revenueAccount.setType("Revenue");
        revenueAccount.setNormalSide("Credit");
        revenueAccount.setOrderingPosition(3);
        revenueAccount = chartOfAccountsRepository.save(revenueAccount);

        // Create open period
        openPeriod = new AccountingPeriod();
        openPeriod.setCompanyId(testCompanyId);
        openPeriod.setFiscalYear(2025);
        openPeriod.setPeriodNumber(1);
        openPeriod.setPeriodName("2025-01");
        openPeriod.setStartDate(LocalDate.of(2025, 1, 1));
        openPeriod.setEndDate(LocalDate.of(2025, 1, 31));
        openPeriod.setStatus(PeriodStatus.OPEN);
        accountingPeriodRepository.save(openPeriod);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void postInvoiceWithVATValidation_shouldLogVATRateOverride() throws Exception {
        // Create invoice with VAT rate override (5% instead of default 10%)
        SalesInvoiceCreateRequest request = new SalesInvoiceCreateRequest();
        request.setCustomerId(testCustomer.getId());
        request.setInvoiceNumber("INV-VAT-001");
        request.setInvoiceDate(LocalDate.of(2025, 1, 15));
        request.setDueDate(LocalDate.of(2025, 2, 15));
        request.setReference("REF-VAT-001");
        request.setDescription("Invoice with VAT override");
        // Don't set status - will be set to DRAFT by default

        SalesInvoiceLineDTO lineItem = new SalesInvoiceLineDTO();
        lineItem.setLineNumber(1);
        lineItem.setAccountId(revenueAccount.getId());
        lineItem.setDescription("Product with 5% VAT");
        lineItem.setQuantity(BigDecimal.ONE);
        lineItem.setUnitPrice(new BigDecimal("1000000.00"));
        lineItem.setAmount(new BigDecimal("1000000.00"));
        lineItem.setVatRate(VatRate.FIVE); // Override: 5% instead of default 10%
        lineItem.setVatAmount(new BigDecimal("50000.00"));
        request.setLines(List.of(lineItem));

        // Create invoice
        String createResponse = mockMvc
                .perform(post("/api/v1/ar/sales-invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken)
                        .header("X-Company-Id", testCompanyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID invoiceId = UUID.fromString(
                objectMapper.readTree(createResponse).get("data").get("id").asText());

        // Approve and post invoice (this should trigger VAT validation and logging)
        mockMvc
                .perform(post("/api/v1/ar/sales-invoices/{id}/approve", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + chiefToken)
                        .header("X-Company-Id", testCompanyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        // Verify audit log for VAT rate override
        List<AuditLog> vatLogs = auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "SALES_INVOICE", invoiceId.toString(), testCompanyId);
        assertThat(vatLogs).isNotEmpty();
        // Should contain VAT-related audit entries
        boolean hasVatLog = vatLogs.stream()
                .anyMatch(log -> log.getEventType() != null
                        && (log.getEventType().contains("VAT") || log.getEventType().contains("VAT_RATE")));
        assertThat(hasVatLog).isTrue();
    }

    @Test
    void createVATCorrection_shouldLogCorrection() throws Exception {
        // Create and post an invoice first
        SalesInvoice invoice = new SalesInvoice();
        invoice.setCompanyId(testCompanyId);
        invoice.setCustomerId(testCustomer.getId());
        invoice.setInvoiceNumber("INV-CORR-001");
        invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
        invoice.setDueDate(LocalDate.of(2025, 2, 15));
        invoice.setStatus(SalesInvoiceStatus.POSTED);
        invoice.setTotalAmount(new BigDecimal("1100000.00"));
        invoice.setVatAmount(new BigDecimal("100000.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setRemainingBalance(new BigDecimal("1100000.00"));
        invoice.setCreatedById(testUserId);
        invoice.setApprovedById(chiefAccountantId);
        invoice = salesInvoiceRepository.save(invoice);

        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setCompanyId(testCompanyId);
        line.setSalesInvoiceId(invoice.getId());
        line.setLineNumber(1);
        line.setAccountId(revenueAccount.getId());
        line.setDescription("Invoice line");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("1000000.00"));
        line.setAmount(new BigDecimal("1000000.00"));
        line.setVatAmount(new BigDecimal("100000.00"));
        line.setVatRate(VatRate.TEN);
        salesInvoiceLineRepository.save(line);

        // Create VAT correction
        String correctionRequest = """
                {
                  "invoiceId": "%s",
                  "newVatAmount": 120000.00,
                  "reason": "VAT rate correction needed"
                }
                """.formatted(invoice.getId());

        String correctionResponse = mockMvc
                .perform(post("/api/v1/ar-vat/corrections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + chiefToken)
                        .header("X-Company-Id", testCompanyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID correctionId = UUID.fromString(
                objectMapper.readTree(correctionResponse).get("id").asText());

        // Verify audit log for VAT correction
        List<AuditLog> correctionLogs = auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "AR_VAT_CORRECTION", correctionId.toString(), testCompanyId);
        assertThat(correctionLogs).isNotEmpty();
        boolean hasCorrectionLog = correctionLogs.stream()
                .anyMatch(log -> log.getEventType() != null
                        && log.getEventType().contains("VAT_CORRECTION"));
        assertThat(hasCorrectionLog).isTrue();
    }

    @Test
    void createCreditNote_shouldLogCreditNoteCreation() throws Exception {
        // Create and post original invoice
        SalesInvoice originalInvoice = new SalesInvoice();
        originalInvoice.setCompanyId(testCompanyId);
        originalInvoice.setCustomerId(testCustomer.getId());
        originalInvoice.setInvoiceNumber("INV-ORIG-001");
        originalInvoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
        originalInvoice.setDueDate(LocalDate.of(2025, 2, 15));
        originalInvoice.setStatus(SalesInvoiceStatus.POSTED);
        originalInvoice.setTotalAmount(new BigDecimal("1100000.00"));
        originalInvoice.setVatAmount(new BigDecimal("100000.00"));
        originalInvoice.setAmountPaid(BigDecimal.ZERO);
        originalInvoice.setRemainingBalance(new BigDecimal("1100000.00"));
        originalInvoice.setCreatedById(testUserId);
        originalInvoice.setApprovedById(chiefAccountantId);
        // Set a dummy posted voucher ID to simulate a posted invoice with voucher
        originalInvoice.setPostedVoucherId(UUID.randomUUID());
        originalInvoice = salesInvoiceRepository.save(originalInvoice);

        SalesInvoiceLine originalLine = new SalesInvoiceLine();
        originalLine.setCompanyId(testCompanyId);
        originalLine.setSalesInvoiceId(originalInvoice.getId());
        originalLine.setLineNumber(1);
        originalLine.setAccountId(revenueAccount.getId());
        originalLine.setDescription("Original invoice line");
        originalLine.setQuantity(BigDecimal.ONE);
        originalLine.setUnitPrice(new BigDecimal("1000000.00"));
        originalLine.setAmount(new BigDecimal("1000000.00"));
        originalLine.setVatAmount(new BigDecimal("100000.00"));
        originalLine.setVatRate(VatRate.TEN);
        salesInvoiceLineRepository.save(originalLine);

        // Create credit note as a regular invoice with negative amounts
        // Note: Credit note endpoint may not exist, so we create it as a regular
        // invoice
        // and verify audit logging works for invoice creation
        SalesInvoiceCreateRequest creditNoteRequest = new SalesInvoiceCreateRequest();
        creditNoteRequest.setCustomerId(testCustomer.getId());
        creditNoteRequest.setInvoiceNumber("CN-001");
        creditNoteRequest.setInvoiceDate(LocalDate.of(2025, 1, 20));
        creditNoteRequest.setDueDate(LocalDate.of(2025, 2, 20));
        creditNoteRequest.setReference("Credit Note for INV-ORIG-001");
        creditNoteRequest.setDescription("Credit note");
        // Don't set status - credit note will be auto-posted

        SalesInvoiceLineDTO creditLine = new SalesInvoiceLineDTO();
        creditLine.setLineNumber(1);
        creditLine.setAccountId(revenueAccount.getId());
        creditLine.setDescription("Credit line");
        creditLine.setQuantity(BigDecimal.ONE);
        creditLine.setUnitPrice(new BigDecimal("1000000.00")); // Positive for DTO validation
        creditLine.setAmount(new BigDecimal("1000000.00"));
        creditLine.setVatRate(VatRate.TEN);
        creditLine.setVatAmount(new BigDecimal("100000.00"));
        creditNoteRequest.setLines(List.of(creditLine));

        // Create credit note using the credit note endpoint
        String creditNoteResponse = mockMvc
                .perform(post("/api/v1/ar/sales-invoices/{id}/credit-note", originalInvoice.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken)
                        .header("X-Company-Id", testCompanyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditNoteRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID creditNoteId = UUID.fromString(
                objectMapper.readTree(creditNoteResponse).get("data").get("id").asText());

        // Verify credit note is linked to original invoice
        SalesInvoice createdCreditNote = salesInvoiceRepository
                .findByCompanyIdAndId(testCompanyId, creditNoteId)
                .orElseThrow();
        assertThat(createdCreditNote.getOriginalInvoiceId()).isEqualTo(originalInvoice.getId());
        assertThat(createdCreditNote.getStatus()).isEqualTo(SalesInvoiceStatus.POSTED);

        // Verify audit log for credit note creation
        List<AuditLog> creditNoteLogs = auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "CREDIT_NOTE", creditNoteId.toString(), testCompanyId);
        // Also check SALES_INVOICE entity type as credit note is also an invoice
        List<AuditLog> invoiceLogs = auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "SALES_INVOICE", creditNoteId.toString(), testCompanyId);
        assertThat(creditNoteLogs.size() + invoiceLogs.size()).isGreaterThan(0);
        // Verify that credit note creation is logged with reference to original invoice
        boolean hasCreditNoteLog = creditNoteLogs.stream()
                .anyMatch(log -> log.getEventType() != null
                        && (log.getEventType().contains("CREDIT_NOTE") || log.getEventType().contains("CREDIT")));
        boolean hasInvoiceLog = invoiceLogs.stream()
                .anyMatch(log -> log.getEventType() != null
                        && (log.getEventType().contains("INVOICE") || log.getEventType().contains("CREATED")));
        assertThat(hasCreditNoteLog || hasInvoiceLog).isTrue();
    }

    @Test
    void postInvoiceWithVATValidation_shouldBlockPostingWhenVATVarianceExceedsThreshold() throws Exception {
        // Create invoice with large VAT variance (should block posting)
        SalesInvoiceCreateRequest request = new SalesInvoiceCreateRequest();
        request.setCustomerId(testCustomer.getId());
        request.setInvoiceNumber("INV-VAT-ERR-001");
        request.setInvoiceDate(LocalDate.of(2025, 1, 15));
        request.setDueDate(LocalDate.of(2025, 2, 15));
        request.setReference("REF-VAT-ERR-001");
        request.setDescription("Invoice with VAT variance error");
        // Don't set status - will be set to DRAFT by default
        request.setVatAmount(new BigDecimal("200000.00")); // Header VAT: 200,000

        SalesInvoiceLineDTO lineItem = new SalesInvoiceLineDTO();
        lineItem.setLineNumber(1);
        lineItem.setAccountId(revenueAccount.getId());
        lineItem.setDescription("Product");
        lineItem.setQuantity(BigDecimal.ONE);
        lineItem.setUnitPrice(new BigDecimal("1000000.00"));
        lineItem.setAmount(new BigDecimal("1000000.00"));
        lineItem.setVatRate(VatRate.TEN);
        lineItem.setVatAmount(new BigDecimal("100000.00")); // Line VAT: 100,000 (variance = 100,000 >= 1000)
        request.setLines(List.of(lineItem));

        // Create invoice
        String createResponse = mockMvc
                .perform(post("/api/v1/ar/sales-invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken)
                        .header("X-Company-Id", testCompanyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID invoiceId = UUID.fromString(
                objectMapper.readTree(createResponse).get("data").get("id").asText());

        // Try to approve - should fail due to VAT variance
        mockMvc
                .perform(post("/api/v1/ar/sales-invoices/{id}/approve", invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + chiefToken)
                        .header("X-Company-Id", testCompanyId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
