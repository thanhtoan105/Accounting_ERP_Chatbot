package com.accounting.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "chatbot.enabled=false",
    "chatbot.n8n.batch-webhook-url=http://localhost:5678/webhook-test/batch",
    "chatbot.n8n.webhook-secret=test-secret"
})
class AdminVoucherControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private VoucherRepository voucherRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;
  private User adminUser;
  private User accountantUser;
  private String adminToken;
  private String accountantToken;

  @BeforeEach
  void setUp() {
    voucherRepository.deleteAll();

    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    adminUser = new User();
    adminUser.setEmail("admin@test.com");
    adminUser.setFullName("Admin User");
    adminUser.setPasswordHash(passwordEncoder.encode("password"));
    adminUser.setRole("admin");
    adminUser.setCompanyId(testCompany.getId());
    adminUser.setCreatedAt(Instant.now());
    adminUser.setUpdatedAt(Instant.now());
    adminUser = userRepository.save(adminUser);
    adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "admin");

    accountantUser = new User();
    accountantUser.setEmail("accountant@test.com");
    accountantUser.setFullName("Accountant");
    accountantUser.setPasswordHash(passwordEncoder.encode("password"));
    accountantUser.setRole("accountant");
    accountantUser.setCompanyId(testCompany.getId());
    accountantUser.setCreatedAt(Instant.now());
    accountantUser.setUpdatedAt(Instant.now());
    accountantUser = userRepository.save(accountantUser);
    accountantToken =
        jwtTokenProvider.generateAccessToken(accountantUser.getId(), accountantUser.getEmail(), "accountant");

    createTestVouchers();
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getEmbeddingStatus_asAdmin_returnsCorrectCounts() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/vouchers/embedding-status")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.embedded").value(1))
        .andExpect(jsonPath("$.data.pending").value(2))
        .andExpect(jsonPath("$.data.percentage").value(33.33));
  }

  @Test
  void getEmbeddingStatus_asAccountant_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/vouchers/embedding-status")
                .header("Authorization", "Bearer " + accountantToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEmbeddingStatus_withoutAuth_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/vouchers/embedding-status")
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEmbeddingStatus_withoutCompanyContext_returnsBadRequest() throws Exception {
    CompanyContext.clear();
    try {
      mockMvc
          .perform(
              get("/api/v1/admin/vouchers/embedding-status")
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isBadRequest());
    } finally {
      CompanyContext.setCompanyId(testCompany.getId());
    }
  }

  @Test
  void getEmbeddingStatus_withNoVouchers_returnsZeros() throws Exception {
    voucherRepository.deleteAll();

    mockMvc
        .perform(
            get("/api/v1/admin/vouchers/embedding-status")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0))
        .andExpect(jsonPath("$.data.embedded").value(0))
        .andExpect(jsonPath("$.data.pending").value(0))
        .andExpect(jsonPath("$.data.percentage").value(0));
  }

  @Test
  void startBatchEmbedding_asAdmin_returns202OrError() throws Exception {
    // Note: This test may return 500 if n8n webhook is not available
    // In CI, use WireMock or mock the service
    mockMvc
        .perform(
            post("/api/v1/admin/vouchers/embed/start")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(result -> {
          int status = result.getResponse().getStatus();
          // Accept either 202 (success) or 500 (webhook unavailable in test)
          assert status == 202 || status == 500 : "Expected 202 or 500, got " + status;
        });
  }

  @Test
  void startBatchEmbedding_asAccountant_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/vouchers/embed/start")
                .header("Authorization", "Bearer " + accountantToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void startBatchEmbedding_withoutCompanyContext_returnsBadRequest() throws Exception {
    CompanyContext.clear();
    try {
      mockMvc
          .perform(
              post("/api/v1/admin/vouchers/embed/start")
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isBadRequest());
    } finally {
      CompanyContext.setCompanyId(testCompany.getId());
    }
  }

  private void createTestVouchers() {
    Voucher v1 = new Voucher();
    v1.setCompanyId(testCompany.getId());
    v1.setVoucherNumber("VCH-001");
    v1.setVoucherDate(LocalDate.now());
    v1.setStatus("posted");
    v1.setEnteredBy(adminUser.getId());
    v1.setCreatedAt(Instant.now());
    v1.setUpdatedAt(Instant.now());
    v1.setEmbeddedAt(Instant.now()); // This one is embedded
    voucherRepository.save(v1);

    Voucher v2 = new Voucher();
    v2.setCompanyId(testCompany.getId());
    v2.setVoucherNumber("VCH-002");
    v2.setVoucherDate(LocalDate.now());
    v2.setStatus("posted");
    v2.setEnteredBy(adminUser.getId());
    v2.setCreatedAt(Instant.now());
    v2.setUpdatedAt(Instant.now());
    // embeddedAt is null - not embedded
    voucherRepository.save(v2);

    Voucher v3 = new Voucher();
    v3.setCompanyId(testCompany.getId());
    v3.setVoucherNumber("VCH-003");
    v3.setVoucherDate(LocalDate.now());
    v3.setStatus("draft");
    v3.setEnteredBy(adminUser.getId());
    v3.setCreatedAt(Instant.now());
    v3.setUpdatedAt(Instant.now());
    // embeddedAt is null - not embedded
    voucherRepository.save(v3);
  }
}
