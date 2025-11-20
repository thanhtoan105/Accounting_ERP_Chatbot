package com.accounting.controller.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.dto.InputVATReportDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.VATCorrection;
import com.accounting.entity.VATReportHistory;
import com.accounting.entity.VatRate;
import com.accounting.entity.User;
import com.accounting.service.impl.purchase.VATServiceImpl;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VATCorrectionRepository;
import com.accounting.repository.VATReportHistoryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootTest(classes = VATControllerIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VATControllerIntegrationTest extends com.accounting.test.IntegrationTest {

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
  private SupplierRepository supplierRepository;
  @Autowired
  private PurchaseBillRepository purchaseBillRepository;
  @Autowired
  private PurchaseBillLineRepository purchaseBillLineRepository;
  @Autowired
  private AccountingPeriodRepository accountingPeriodRepository;
  @Autowired
  private ChartOfAccountsRepository chartOfAccountsRepository;
  @Autowired
  private VATReportHistoryRepository vatReportHistoryRepository;
  @Autowired
  private VATCorrectionRepository vatCorrectionRepository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;
  private User chiefAccountant;
  private Supplier testSupplier;
  private ChartOfAccount expenseAccount;
  private AccountingPeriod openPeriod;
  private PurchaseBill postedBill;
  private String accessToken;

  @BeforeEach
  void setUp() {
    testCompany = new Company();
    testCompany.setCode("VATCO");
    testCompany.setName("VAT Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("123 Le Loi, HCMC");
    testCompany.setContactEmail("ops@vatco.test");
    testCompany.setContactPhone("+84 28 9999 9999");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    CompanySettings settings = new CompanySettings();
    settings.setCompanyId(testCompany.getId());
    settings.setDefaultCurrency("VND");
    settings.setTimezone("Asia/Ho_Chi_Minh");
    settings.setDateFormat("DD/MM/YYYY");
    settings.setApprovalThresholdAmount(new BigDecimal("20000000.00"));
    companySettingsRepository.save(settings);

    chiefAccountant = new User();
    chiefAccountant.setCompanyId(testCompany.getId());
    chiefAccountant.setEmail("chief@example.com");
    chiefAccountant.setPasswordHash(passwordEncoder.encode("Password123!"));
    chiefAccountant.setFullName("Chief Accountant");
    chiefAccountant.setRole("chief_accountant");
    chiefAccountant.setStatus("ACTIVE");
    chiefAccountant.setCreatedAt(Instant.now());
    chiefAccountant.setUpdatedAt(Instant.now());
    chiefAccountant = userRepository.save(chiefAccountant);

    accessToken = jwtTokenProvider.generateAccessToken(
            chiefAccountant.getId(), chiefAccountant.getEmail(), chiefAccountant.getRole());

    testSupplier = new Supplier();
    testSupplier.setCompanyId(testCompany.getId());
    testSupplier.setCode("SUP-001");
    testSupplier.setName("Alpha Supplies");
    testSupplier.setTaxCode("0123456789");
    testSupplier.setAddress("456 Dien Bien Phu");
    testSupplier = supplierRepository.save(testSupplier);

    expenseAccount = new ChartOfAccount();
    expenseAccount.setCompanyId(testCompany.getId());
    expenseAccount.setCode("621");
    expenseAccount.setName("Direct costs");
    expenseAccount.setType("Expense");
    expenseAccount.setNormalSide("Debit");
    expenseAccount.setPostable(true);
    expenseAccount.setOrderingPosition(1);
    expenseAccount = chartOfAccountsRepository.save(expenseAccount);

    openPeriod = new AccountingPeriod();
    openPeriod.setCompanyId(testCompany.getId());
    openPeriod.setFiscalYear(2025);
    openPeriod.setPeriodNumber(1);
    openPeriod.setPeriodName("January 2025");
    openPeriod.setStartDate(LocalDate.of(2025, 1, 1));
    openPeriod.setEndDate(LocalDate.of(2025, 1, 31));
    openPeriod.setStatus(PeriodStatus.OPEN);
    openPeriod = accountingPeriodRepository.save(openPeriod);

    postedBill = new PurchaseBill();
    postedBill.setCompanyId(testCompany.getId());
    postedBill.setSupplierId(testSupplier.getId());
    postedBill.setBillNumber("BILL-001");
    postedBill.setBillDate(LocalDate.of(2025, 1, 5));
    postedBill.setDueDate(LocalDate.of(2025, 2, 4));
    postedBill.setReference("REF-001");
    postedBill.setDescription("Office supplies");
    postedBill.setStatus(PurchaseBillStatus.POSTED);
    postedBill.setTotalAmount(new BigDecimal("1600000.00"));
    postedBill.setVatAmount(new BigDecimal("100000.00"));
    postedBill.setCreatedById(chiefAccountant.getId());
    postedBill.setIsSensitive(false);
    postedBill.setSupplier(testSupplier);
    postedBill = purchaseBillRepository.save(postedBill);

    createLine(postedBill, 1, new BigDecimal("1000000.00"), new BigDecimal("100000.00"), VatRate.TEN);
    createLine(postedBill, 2, new BigDecimal("500000.00"), BigDecimal.ZERO, VatRate.ZERO);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void generateInputReport_shouldReturnAggregatedDataAndPersistHistory() throws Exception {
    InputVATReportDTO report = requestInputVatReport("EXCEL");

    assertThat(report.getItems()).hasSize(2);
    assertThat(report.getGrandTotalVAT()).isEqualByComparingTo("100000.00");
    assertThat(report.getItems().get(0).getSupplierName()).isEqualTo(testSupplier.getName());

    List<VATReportHistory> histories = vatReportHistoryRepository.findAll();
    assertThat(histories).hasSize(1);
    assertThat(histories.get(0).getCompanyId()).isEqualTo(testCompany.getId());
  }

  @Test
  void exportInputReport_shouldReturnPdfAndIncrementDownloadCount() throws Exception {
    InputVATReportDTO report = requestInputVatReport("PDF");

    mockMvc
        .perform(
            get("/api/v1/vat/reports/{reportId}/export", report.getReportId())
                .param("format", "PDF")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".pdf")))
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));

    List<VATReportHistory> histories = vatReportHistoryRepository.findAll();
    assertThat(histories).hasSize(1);
    VATReportHistory persisted = histories.get(0);
    assertThat(persisted.getDownloadCount()).isEqualTo(1);
  }

  @Test
  void createVATCorrection_shouldCreatePendingCorrection() throws Exception {
    List<PurchaseBillLine> lines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        testCompany.getId(), postedBill.getId());
    assertThat(lines).isNotEmpty();
    PurchaseBillLine line = lines.get(0);

    String body = """
        {
          "purchaseBillId": "%s",
          "purchaseBillLineId": "%s",
          "newVatAmount": "150000.00",
          "reason": "Supplier provided corrected invoice"
        }
        """
        .formatted(postedBill.getId(), line.getId());

    mockMvc
        .perform(
            post("/api/v1/vat/corrections")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId()))
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.oldVatAmount").value(100000.00))
        .andExpect(jsonPath("$.newVatAmount").value(150000.00))
        .andExpect(jsonPath("$.difference").value(50000.00))
        .andExpect(jsonPath("$.reason").value("Supplier provided corrected invoice"));

    List<VATCorrection> corrections = vatCorrectionRepository.findAll();
    assertThat(corrections).hasSize(1);
    assertThat(corrections.get(0).getCompanyId()).isEqualTo(testCompany.getId());
    assertThat(corrections.get(0).getStatus()).isEqualTo(VATCorrection.Status.PENDING);
  }

  @Test
  void approveVATCorrection_shouldApplyCorrectionAndUpdateBill() throws Exception {
    List<PurchaseBillLine> lines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        testCompany.getId(), postedBill.getId());
    assertThat(lines).isNotEmpty();
    PurchaseBillLine line = lines.get(0);

    // Create a correction first
    VATCorrection correction = new VATCorrection();
    correction.setCompanyId(testCompany.getId());
    correction.setPurchaseBillId(postedBill.getId());
    correction.setPurchaseBillLineId(line.getId());
    correction.setOldVatAmount(new BigDecimal("100000.00"));
    correction.setNewVatAmount(new BigDecimal("150000.00"));
    correction.setReason("Test correction");
    correction.setStatus(VATCorrection.Status.PENDING);
    correction.setCorrectedById(chiefAccountant.getId());
    correction.setCorrectedAt(Instant.now());
    correction = vatCorrectionRepository.save(correction);

    mockMvc
        .perform(
            post("/api/v1/vat/corrections/{correctionId}/approve", correction.getId())
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedById").value(chiefAccountant.getId()));

    // Verify correction was applied to line
    PurchaseBillLine updatedLine = purchaseBillLineRepository.findById(line.getId()).orElse(null);
    assertThat(updatedLine).isNotNull();
    assertThat(updatedLine.getVatAmount()).isEqualByComparingTo("150000.00");
  }

  @Test
  void listVATCorrections_shouldReturnFilteredCorrections() throws Exception {
    List<PurchaseBillLine> lines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        testCompany.getId(), postedBill.getId());
    assertThat(lines).isNotEmpty();
    PurchaseBillLine line = lines.get(0);

    // Create multiple corrections
    VATCorrection correction1 = new VATCorrection();
    correction1.setCompanyId(testCompany.getId());
    correction1.setPurchaseBillId(postedBill.getId());
    correction1.setPurchaseBillLineId(line.getId());
    correction1.setOldVatAmount(new BigDecimal("100000.00"));
    correction1.setNewVatAmount(new BigDecimal("120000.00"));
    correction1.setReason("Correction 1");
    correction1.setStatus(VATCorrection.Status.PENDING);
    correction1.setCorrectedById(chiefAccountant.getId());
    correction1.setCorrectedAt(Instant.now());
    vatCorrectionRepository.save(correction1);

    VATCorrection correction2 = new VATCorrection();
    correction2.setCompanyId(testCompany.getId());
    correction2.setPurchaseBillId(postedBill.getId());
    correction2.setOldVatAmount(new BigDecimal("100000.00"));
    correction2.setNewVatAmount(new BigDecimal("80000.00"));
    correction2.setReason("Correction 2");
    correction2.setStatus(VATCorrection.Status.APPROVED);
    correction2.setCorrectedById(chiefAccountant.getId());
    correction2.setCorrectedAt(Instant.now());
    correction2.setApprovedById(chiefAccountant.getId());
    correction2.setApprovedAt(Instant.now());
    vatCorrectionRepository.save(correction2);

    mockMvc
        .perform(
            get("/api/v1/vat/corrections")
                .param("billId", postedBill.getId().toString())
                .param("status", "PENDING")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  private InputVATReportDTO requestInputVatReport(String format) throws Exception {
    // Use periodId from the test setup
    String body = """
        {
          "periodId": "%s",
          "supplierId": %d,
          "filters": {
            "format": "%s"
          }
        }
        """
        .formatted(openPeriod.getId(), testSupplier.getId(), format);

    MvcResult result = mockMvc
            .perform(
                post("/api/v1/vat/reports/input")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Company-Id", String.valueOf(testCompany.getId()))
                    .contentType(APPLICATION_JSON)
                    .content(body))
            .andReturn();
    
    if (result.getResponse().getStatus() != 200) {
      System.err.println("Response status: " + result.getResponse().getStatus());
      System.err.println("Response body: " + result.getResponse().getContentAsString());
      System.err.println("Response headers: " + result.getResponse().getHeaderNames());
      if (result.getResolvedException() != null) {
        System.err.println("Exception: " + result.getResolvedException().getMessage());
        result.getResolvedException().printStackTrace();
      }
    }
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    return objectMapper.readValue(result.getResponse().getContentAsByteArray(), InputVATReportDTO.class);
  }

  private void createLine(
      PurchaseBill bill, int lineNumber, BigDecimal amount, BigDecimal vatAmount, VatRate rate) {
    PurchaseBillLine line = new PurchaseBillLine();
    line.setCompanyId(testCompany.getId());
    line.setPurchaseBillId(bill.getId());
    line.setLineNumber(lineNumber);
    line.setAccountId(expenseAccount.getId());
    line.setDescription("Line " + lineNumber);
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPrice(amount);
    line.setAmount(amount);
    line.setVatAmount(vatAmount);
    line.setVatRate(rate);
    purchaseBillLineRepository.save(line);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(basePackages = "com.accounting", excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.accounting.service.impl.purchase.VATServiceImpl.class))
  static class TestApplication {
  }
}
