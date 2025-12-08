package com.accounting.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ReminderRequestDTO;
import com.accounting.entity.*;
import com.accounting.repository.*;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for AP Aging endpoints.
 * Tests the full flow from HTTP request to database and back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class APAgingIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private SupplierRepository supplierRepository;

  @Autowired private PurchaseBillRepository purchaseBillRepository;

  @Autowired private APPaymentRepository apPaymentRepository;

  @Autowired private PaymentAllocationRepository paymentAllocationRepository;

  @Autowired private AccountingPeriodRepository periodRepository;

  @Autowired private AuditLogRepository auditLogRepository;

  @SpyBean private AuditService auditService;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper objectMapper;

  private Long testCompanyId;
  private Long testUserId;
  private Long testSupplierId;
  private UUID testPeriodId;
  private PurchaseBill testBill;
  private LocalDate asOfDate;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(null);

    // Create test company
    Company company = new Company();
    company.setCode("TEST001");
    company.setName("Test Company");
    company.setTaxCode("1234567890");
    company.setAddress("Test Address");
    company = companyRepository.save(company);
    testCompanyId = company.getId();
    CompanyContext.setCompanyId(testCompanyId);

    // Create test user
    User user = new User();
    user.setEmail("test@example.com");
    user.setPasswordHash(passwordEncoder.encode("Password123!"));
    user.setFullName("Test User");
    user.setCompanyId(testCompanyId);
    user.setRole("chief_accountant");
    user.setStatus("ACTIVE");
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);
    testUserId = user.getId();

    // Create test supplier
    Supplier supplier = new Supplier();
    supplier.setCompanyId(testCompanyId);
    supplier.setName("Test Supplier");
    supplier.setCode("SUP001");
    supplier.setActive(true);
    supplier = supplierRepository.save(supplier);
    testSupplierId = supplier.getId();

    // Create test period
    AccountingPeriod period = new AccountingPeriod();
    period.setCompanyId(testCompanyId);
    period.setFiscalYear(LocalDate.now().getYear());
    period.setPeriodNumber(1);
    period.setPeriodName("Test Period");
    period.setStartDate(LocalDate.now().minusMonths(1));
    period.setEndDate(LocalDate.now().plusMonths(1));
    period.setStatus(PeriodStatus.OPEN);
    period = periodRepository.save(period);
    testPeriodId = period.getId();

    // Create test purchase bill
    asOfDate = LocalDate.now();
    testBill = new PurchaseBill();
    testBill.setCompanyId(testCompanyId);
    testBill.setSupplierId(testSupplierId);
    testBill.setBillNumber("BILL-001");
    testBill.setBillDate(LocalDate.now().minusDays(60));
    testBill.setDueDate(asOfDate.minusDays(30)); // 30 days overdue
    testBill.setTotalAmount(new BigDecimal("10000.00"));
    testBill.setVatAmount(BigDecimal.ZERO);
    testBill.setReference("REF-001");
    testBill.setDescription("Integration test bill");
    testBill.setStatus(PurchaseBillStatus.POSTED);
    testBill.setCreatedById(testUserId);
    testBill.setIsSensitive(false);
    testBill.setCreatedAt(Instant.now());
    testBill = purchaseBillRepository.save(testBill);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getAgingReport_returnsAgingData() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging")
                .param("asOfDate", asOfDate.toString())
                .param("page", "0")
                .param("size", "20")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").exists());
  }

  @Test
  void getAgingReport_withSupplierFilter_returnsFilteredData() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging")
                .param("supplier", testSupplierId.toString())
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void getOverdueCount_returnsCount() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging/overdue/count")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").exists());
  }

  @Test
  void getOverdueSuppliers_returnsSuppliers() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging/overdue")
                .param("asOfDate", asOfDate.toString())
                .param("limit", "10")
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void getAgingBillDetails_returnsBillDetails() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging/{supplierId}/bills", testSupplierId)
                .param("bucket", "DAYS_1_30")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void exportAgingReport_excel_returnsExcelFile() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging/export")
                .param("format", "EXCEL")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .contentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  @Test
  void sendReminder_returnsSuccess() throws Exception {
    ReminderRequestDTO request = new ReminderRequestDTO();
    request.setSupplierId(testSupplierId);
    request.setRecipients(java.util.List.of("test@example.com"));
    request.setMessage("Test reminder");

    mockMvc
        .perform(
            post("/api/v1/ap-aging/remind")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").exists());
  }

  @Test
  void getAgingReport_forbiddenForViewerRole() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/ap-aging")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(2L, "VIEWER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getAgingReport_logsAuditEntry() throws Exception {
    auditLogRepository.deleteAll();

    mockMvc
        .perform(
            get("/api/v1/ap-aging")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk());

    assertThat(auditLogRepository.findAll())
        .anyMatch(log -> "AGING_REPORT_VIEWED".equals(log.getAction()));
  }

  @Test
  void getAgingBillDetails_logsAuditEntry() throws Exception {
    auditLogRepository.deleteAll();

    mockMvc
        .perform(
            get("/api/v1/ap-aging/{supplierId}/bills", testSupplierId)
                .param("bucket", "DAYS_1_30")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk());

    assertThat(auditLogRepository.findAll())
        .anyMatch(log -> "AGING_DRILLDOWN_VIEWED".equals(log.getAction()));
  }

  @Test
  void exportAgingReport_logsAuditEntry() throws Exception {
    auditLogRepository.deleteAll();

    mockMvc
        .perform(
            get("/api/v1/ap-aging/export")
                .param("asOfDate", asOfDate.toString())
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk());

    assertThat(auditLogRepository.findAll())
        .anyMatch(log -> "REPORT_EXPORT".equals(log.getAction()));
  }

  @Test
  void sendReminder_logsAuditEntry() throws Exception {
    auditLogRepository.deleteAll();
    ReminderRequestDTO request = new ReminderRequestDTO();
    request.setSupplierId(testSupplierId);
    request.setRecipients(List.of("test@example.com"));
    request.setMessage("Reminder");

    mockMvc
        .perform(
            post("/api/v1/ap-aging/remind")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Company-Id", testCompanyId.toString())
                .with(authenticatedUser(testUserId, "CHIEF_ACCOUNTANT")))
        .andExpect(status().isOk());

    assertThat(auditLogRepository.findAll())
        .anyMatch(log -> "AGING_REMINDER_SENT".equals(log.getAction()));
  }

  private RequestPostProcessor authenticatedUser(Long userId, String... roles) {
    List<GrantedAuthority> authorities =
        Arrays.stream(roles)
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            String.valueOf(userId), "password", authorities);
    return SecurityMockMvcRequestPostProcessors.authentication(authentication);
  }
}
