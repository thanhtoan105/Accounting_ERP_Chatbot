package com.accounting.controller.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.accounting.dto.ARVATCorrectionCreateRequest;
import com.accounting.dto.OutputVATReportDTO;
import com.accounting.entity.ARVATCorrection;
import com.accounting.entity.AccountingPeriod;
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
import com.accounting.repository.ARVATCorrectionRepository;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = ARVATControllerIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ARVATControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private CompanyRepository companyRepository;
  @Autowired
  private CompanySettingsRepository companySettingsRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private SalesInvoiceRepository salesInvoiceRepository;
  @Autowired
  private SalesInvoiceLineRepository salesInvoiceLineRepository;
  @Autowired
  private AccountingPeriodRepository accountingPeriodRepository;
  @Autowired
  private ChartOfAccountsRepository chartOfAccountsRepository;
  @Autowired
  private ARVATCorrectionRepository arVatCorrectionRepository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;
  private User chiefAccountant;
  private Customer testCustomer;
  private ChartOfAccount arAccount;
  private ChartOfAccount vatAccount;
  private ChartOfAccount revenueAccount;
  private AccountingPeriod openPeriod;
  private SalesInvoice postedInvoice;
  private String accessToken;

  @BeforeEach
  void setUp() {
    testCompany = new Company();
    testCompany.setCode("ARVATCO");
    testCompany.setName("AR VAT Company");
    testCompany.setTaxCode("9876543210");
    testCompany.setAddress("789 Nguyen Hue, HCMC");
    testCompany.setContactEmail("ops@arvatco.test");
    testCompany.setContactPhone("+84 28 8888 8888");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    CompanySettings settings = new CompanySettings();
    settings.setCompanyId(testCompany.getId());
    settings.setDefaultCurrency("VND");
    settings.setTimezone("Asia/Ho_Chi_Minh");
    settings.setDateFormat("DD/MM/YYYY");
    settings.setSalesInvoiceApprovalThresholdAmount(new BigDecimal("100000000.00"));
    companySettingsRepository.save(settings);

    chiefAccountant = new User();
    chiefAccountant.setCompanyId(testCompany.getId());
    chiefAccountant.setEmail("chief@arvat.test");
    chiefAccountant.setPasswordHash(passwordEncoder.encode("Password123!"));
    chiefAccountant.setFullName("Chief Accountant");
    chiefAccountant.setRole("chief_accountant");
    chiefAccountant.setStatus("ACTIVE");
    chiefAccountant.setCreatedAt(Instant.now());
    chiefAccountant.setUpdatedAt(Instant.now());
    chiefAccountant = userRepository.save(chiefAccountant);

    accessToken = jwtTokenProvider.generateAccessToken(
        chiefAccountant.getId(), chiefAccountant.getEmail(), chiefAccountant.getRole());

    testCustomer = new Customer();
    testCustomer.setCompanyId(testCompany.getId());
    testCustomer.setCode("CUST-001");
    testCustomer.setName("Beta Customer");
    testCustomer.setTaxCode("0987654321");
    testCustomer.setAddress("321 Vo Van Tan");
    testCustomer = customerRepository.save(testCustomer);

    // Create chart of accounts
    arAccount = new ChartOfAccount();
    arAccount.setCompanyId(testCompany.getId());
    arAccount.setCode("131");
    arAccount.setName("Accounts Receivable");
    arAccount.setPostable(true);
    arAccount.setType("Asset");
    arAccount.setNormalSide("Debit");
    arAccount.setOrderingPosition(1);
    arAccount = chartOfAccountsRepository.save(arAccount);

    vatAccount = new ChartOfAccount();
    vatAccount.setCompanyId(testCompany.getId());
    vatAccount.setCode("3331");
    vatAccount.setName("Output VAT");
    vatAccount.setPostable(true);
    vatAccount.setType("Liability");
    vatAccount.setNormalSide("Credit");
    vatAccount.setOrderingPosition(2);
    vatAccount = chartOfAccountsRepository.save(vatAccount);

    revenueAccount = new ChartOfAccount();
    revenueAccount.setCompanyId(testCompany.getId());
    revenueAccount.setCode("511");
    revenueAccount.setName("Sales Revenue");
    revenueAccount.setPostable(true);
    revenueAccount.setType("Revenue");
    revenueAccount.setNormalSide("Credit");
    revenueAccount.setOrderingPosition(3);
    revenueAccount = chartOfAccountsRepository.save(revenueAccount);

    // Create open period
    openPeriod = new AccountingPeriod();
    openPeriod.setCompanyId(testCompany.getId());
    openPeriod.setFiscalYear(2025);
    openPeriod.setPeriodNumber(1);
    openPeriod.setPeriodName("2025-01");
    openPeriod.setStartDate(LocalDate.of(2025, 1, 1));
    openPeriod.setEndDate(LocalDate.of(2025, 1, 31));
    openPeriod.setStatus(PeriodStatus.OPEN);
    openPeriod = accountingPeriodRepository.save(openPeriod);

    // Create posted invoice
    postedInvoice = new SalesInvoice();
    postedInvoice.setCompanyId(testCompany.getId());
    postedInvoice.setInvoiceNumber("INV-001");
    postedInvoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
    postedInvoice.setDueDate(LocalDate.of(2025, 2, 15));
    postedInvoice.setCustomerId(testCustomer.getId());
    postedInvoice.setStatus(SalesInvoiceStatus.POSTED);
    postedInvoice.setTotalAmount(new BigDecimal("1100000.00"));
    postedInvoice.setVatAmount(new BigDecimal("100000.00"));
    postedInvoice.setAmountPaid(BigDecimal.ZERO);
    postedInvoice.setRemainingBalance(new BigDecimal("1100000.00"));
    postedInvoice.setReference("REF-001");
    postedInvoice.setCreatedById(chiefAccountant.getId());
    postedInvoice.setApprovedById(chiefAccountant.getId());
    postedInvoice.setIsSensitive(false);
    postedInvoice = salesInvoiceRepository.save(postedInvoice);

    SalesInvoiceLine line = new SalesInvoiceLine();
    line.setCompanyId(testCompany.getId());
    line.setSalesInvoiceId(postedInvoice.getId());
    line.setLineNumber(1);
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPrice(new BigDecimal("1000000.00"));
    line.setAmount(new BigDecimal("1000000.00"));
    line.setVatAmount(new BigDecimal("100000.00"));
    line.setVatRate(VatRate.TEN);
    line.setAccountId(revenueAccount.getId());
    line.setDescription("Test product");
    salesInvoiceLineRepository.save(line);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void generateVATReport_shouldReturnOutputVATReport() throws Exception {
    MvcResult result = mockMvc
        .perform(get("/api/v1/ar-vat/report")
            .param("periodId", openPeriod.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.companyId").value(testCompany.getId()))
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-001"))
        .andExpect(jsonPath("$.grandTotalVAT").value(100000.00))
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    OutputVATReportDTO report = objectMapper.readValue(responseBody, OutputVATReportDTO.class);
    assertThat(report.getItems()).hasSize(1);
    assertThat(report.getGrandTotalVAT()).isEqualByComparingTo(new BigDecimal("100000.00"));
  }

  @Test
  void exportVATReport_shouldReturnExcelFile() throws Exception {
    // First generate a report
    MvcResult generateResult = mockMvc
        .perform(get("/api/v1/ar-vat/report")
            .param("periodId", openPeriod.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andReturn();

    String generateResponse = generateResult.getResponse().getContentAsString();
    OutputVATReportDTO report = objectMapper.readValue(generateResponse, OutputVATReportDTO.class);
    UUID reportId = report.getReportId();

    // Then export it
    mockMvc
        .perform(get("/api/v1/ar-vat/report/export")
            .param("reportId", reportId.toString())
            .param("format", "EXCEL")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            org.hamcrest.Matchers.containsString("output-vat-report")))
        .andExpect(content().contentType(
            org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
  }

  @Test
  void createCorrection_shouldCreatePendingCorrection() throws Exception {
    ARVATCorrectionCreateRequest request = new ARVATCorrectionCreateRequest();
    request.setInvoiceId(postedInvoice.getId());
    request.setNewVatAmount(new BigDecimal("120000.00"));
    request.setReason("VAT rate correction needed");

    mockMvc
        .perform(post("/api/v1/ar-vat/corrections")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.oldVatAmount").value(100000.00))
        .andExpect(jsonPath("$.newVatAmount").value(120000.00))
        .andExpect(jsonPath("$.reason").value("VAT rate correction needed"));
  }

  @Test
  void createCorrection_shouldRejectWhenInvoiceNotPosted() throws Exception {
    SalesInvoice draftInvoice = new SalesInvoice();
    draftInvoice.setCompanyId(testCompany.getId());
    draftInvoice.setInvoiceNumber("INV-DRAFT");
    draftInvoice.setInvoiceDate(LocalDate.of(2025, 1, 20));
    draftInvoice.setDueDate(LocalDate.of(2025, 2, 20));
    draftInvoice.setCustomerId(testCustomer.getId());
    draftInvoice.setStatus(SalesInvoiceStatus.DRAFT);
    draftInvoice.setTotalAmount(new BigDecimal("1000.00")); // @Positive requires > 0
    draftInvoice.setVatAmount(BigDecimal.ZERO);
    draftInvoice.setAmountPaid(BigDecimal.ZERO);
    draftInvoice.setRemainingBalance(new BigDecimal("1000.00"));
    draftInvoice.setReference("REF-DRAFT");
    draftInvoice.setCreatedById(chiefAccountant.getId());
    draftInvoice.setIsSensitive(false);
    draftInvoice = salesInvoiceRepository.save(draftInvoice);

    ARVATCorrectionCreateRequest request = new ARVATCorrectionCreateRequest();
    request.setInvoiceId(draftInvoice.getId());
    request.setNewVatAmount(new BigDecimal("120000.00"));
    request.setReason("VAT rate correction needed");

    mockMvc
        .perform(post("/api/v1/ar-vat/corrections")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listCorrections_shouldReturnCorrectionsForInvoice() throws Exception {
    // Create a correction first
    ARVATCorrection correction = new ARVATCorrection();
    correction.setCompanyId(testCompany.getId());
    correction.setInvoiceId(postedInvoice.getId());
    correction.setOldVatAmount(new BigDecimal("100000.00"));
    correction.setNewVatAmount(new BigDecimal("120000.00"));
    correction.setReason("Test correction");
    correction.setStatus(ARVATCorrection.Status.PENDING);
    correction.setCorrectedById(chiefAccountant.getId());
    correction.setCorrectedAt(Instant.now());
    correction = arVatCorrectionRepository.save(correction);

    mockMvc
        .perform(get("/api/v1/ar-vat/corrections")
            .param("invoiceId", postedInvoice.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").value(correction.getId().toString()))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  @Test
  void approveCorrection_shouldUpdateInvoiceVATAmount() throws Exception {
    // Create a correction first
    ARVATCorrection correction = new ARVATCorrection();
    correction.setCompanyId(testCompany.getId());
    correction.setInvoiceId(postedInvoice.getId());
    correction.setOldVatAmount(new BigDecimal("100000.00"));
    correction.setNewVatAmount(new BigDecimal("120000.00"));
    correction.setReason("Test correction");
    correction.setStatus(ARVATCorrection.Status.PENDING);
    correction.setCorrectedById(chiefAccountant.getId());
    correction.setCorrectedAt(Instant.now());
    correction = arVatCorrectionRepository.save(correction);

    mockMvc
        .perform(post("/api/v1/ar-vat/corrections/{correctionId}/approve", correction.getId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedById").value(chiefAccountant.getId()));

    // Verify invoice VAT amount was updated
    SalesInvoice updatedInvoice = salesInvoiceRepository
        .findByCompanyIdAndId(testCompany.getId(), postedInvoice.getId())
        .orElseThrow();
    assertThat(updatedInvoice.getVatAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));
  }

  @Test
  void rejectCorrection_shouldUpdateStatusToRejected() throws Exception {
    // Create a correction first
    ARVATCorrection correction = new ARVATCorrection();
    correction.setCompanyId(testCompany.getId());
    correction.setInvoiceId(postedInvoice.getId());
    correction.setOldVatAmount(new BigDecimal("100000.00"));
    correction.setNewVatAmount(new BigDecimal("120000.00"));
    correction.setReason("Test correction");
    correction.setStatus(ARVATCorrection.Status.PENDING);
    correction.setCorrectedById(chiefAccountant.getId());
    correction.setCorrectedAt(Instant.now());
    correction = arVatCorrectionRepository.save(correction);

    mockMvc
        .perform(post("/api/v1/ar-vat/corrections/{correctionId}/reject", correction.getId())
            .param("reason", "Invalid correction request")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("REJECTED"));

    // Verify correction was rejected
    ARVATCorrection updatedCorrection = arVatCorrectionRepository
        .findByCompanyIdAndId(testCompany.getId(), correction.getId())
        .orElseThrow();
    assertThat(updatedCorrection.getStatus()).isEqualTo(ARVATCorrection.Status.REJECTED);
  }

  @org.springframework.boot.SpringBootConfiguration
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.springframework.context.annotation.ComponentScan(
      basePackages = "com.accounting",
      excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
          type = org.springframework.context.annotation.FilterType.REGEX,
          pattern = "com\\.accounting\\.config\\..*"))
  static class TestApplication {
  }
}
