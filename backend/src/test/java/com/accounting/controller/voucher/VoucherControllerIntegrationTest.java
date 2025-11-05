package com.accounting.controller.voucher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VoucherControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private VoucherRepository voucherRepository;

  @Autowired private VoucherLineRepository voucherLineRepository;

  @Autowired private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Autowired private ObjectMapper objectMapper;

  private Company testCompany;
  private Company otherCompany;
  private User testUser;
  private User otherCompanyUser;
  private String testToken;

  @BeforeEach
  void setUp() {
    voucherLineRepository.deleteAll();
    voucherRepository.deleteAll();
    chartOfAccountsRepository.deleteAll();
    userRepository.deleteAll();
    companyRepository.deleteAll();

    // Create test companies
    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);

    otherCompany = new Company();
    otherCompany.setCode("OTHER");
    otherCompany.setName("Other Company");
    otherCompany.setTaxCode("0987654321");
    otherCompany.setAddress("Other Address");
    otherCompany = companyRepository.save(otherCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    // Create test users
    testUser = new User();
    testUser.setEmail("test@example.com");
    testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    testUser.setFullName("Test User");
    testUser.setRole("accountant");
    testUser.setStatus("ACTIVE");
    testUser.setCompanyId(testCompany.getId());
    testUser.setCreatedAt(Instant.now());
    testUser.setUpdatedAt(Instant.now());
    testUser = userRepository.save(testUser);

    otherCompanyUser = new User();
    otherCompanyUser.setEmail("other@example.com");
    otherCompanyUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    otherCompanyUser.setFullName("Other User");
    otherCompanyUser.setRole("accountant");
    otherCompanyUser.setStatus("ACTIVE");
    otherCompanyUser.setCompanyId(otherCompany.getId());
    otherCompanyUser.setCreatedAt(Instant.now());
    otherCompanyUser.setUpdatedAt(Instant.now());
    otherCompanyUser = userRepository.save(otherCompanyUser);

    testToken =
        jwtTokenProvider.generateAccessToken(
            testUser.getId(), testUser.getEmail(), testUser.getRole());

    // Create test ChartOfAccounts for validation
    ChartOfAccount account1 = createPostableAccount("1111", "Cash", testCompany.getId());
    ChartOfAccount account2 = createPostableAccount("4111", "Revenue", testCompany.getId());
    chartOfAccountsRepository.save(account1);
    chartOfAccountsRepository.save(account2);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getVouchers_returnsPaginatedList() throws Exception {
    Voucher voucher1 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher voucher2 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "posted");
    voucherRepository.save(voucher1);
    voucherRepository.save(voucher2);

    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void getVouchers_withStatusFilter_filtersByStatus() throws Exception {
    Voucher draftVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher postedVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "posted");
    voucherRepository.save(draftVoucher);
    voucherRepository.save(postedVoucher);

    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .param("status", "draft")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].status").value("draft"))
        .andExpect(jsonPath("$.data[?(@.status != 'draft')]").doesNotExist());
  }

  @Test
  void getVouchers_withSearchTerm_searchesVoucherNumber() throws Exception {
    Voucher matchingVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    matchingVoucher.setDescription("Payment invoice");
    Voucher nonMatchingVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    nonMatchingVoucher.setDescription("Different description");
    voucherRepository.save(matchingVoucher);
    voucherRepository.save(nonMatchingVoucher);

    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .param("search", "VC2025-001")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].voucherNumber").value("VC2025-001"));
  }

  @Test
  void getVouchers_withSorting_sortsResults() throws Exception {
    Voucher voucher1 =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", LocalDate.of(2025, 1, 2), "draft");
    Voucher voucher2 =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", LocalDate.of(2025, 1, 1), "draft");
    voucherRepository.save(voucher1);
    voucherRepository.save(voucher2);

    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .param("sort", "voucherDate,desc")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].voucherNumber").value("VC2025-001"));
  }

  @Test
  void getVoucherCounts_returnsCountsByStatus() throws Exception {
    Voucher draft1 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher draft2 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", "draft");
    Voucher posted = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-003", "posted");
    voucherRepository.save(draft1);
    voucherRepository.save(draft2);
    voucherRepository.save(posted);

    mockMvc
        .perform(
            get("/api/v1/vouchers/counts")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draft").value(2))
        .andExpect(jsonPath("$.data.posted").value(1))
        .andExpect(jsonPath("$.data.unposted").value(0));
  }

  @Test
  void getVouchers_companyScoping_onlyShowsCurrentCompanyVouchers() throws Exception {
    Voucher company1Voucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    Voucher company2Voucher =
        createVoucher(otherCompany.getId(), otherCompanyUser.getId(), "VC2025-002", "draft");
    voucherRepository.save(company1Voucher);
    voucherRepository.save(company2Voucher);

    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.data[0].voucherNumber").value("VC2025-001"));
  }

  @Test
  void deleteVoucher_draftVoucher_deletesSuccessfully() throws Exception {
    Voucher draftVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    draftVoucher = voucherRepository.save(draftVoucher);

    mockMvc
        .perform(
            delete("/api/v1/vouchers/" + draftVoucher.getId())
                .param("reason", "Test deletion reason")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Voucher deleted successfully"));

    // Verify voucher is deleted
    assertTrue(voucherRepository.findByCompanyIdAndId(testCompany.getId(), draftVoucher.getId()).isEmpty());
  }

  @Test
  void deleteVoucher_postedVoucher_returnsConflict() throws Exception {
    Voucher postedVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "posted");
    postedVoucher = voucherRepository.save(postedVoucher);

    mockMvc
        .perform(
            delete("/api/v1/vouchers/" + postedVoucher.getId())
                .param("reason", "Test deletion reason")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteVoucher_missingReason_returnsBadRequest() throws Exception {
    Voucher draftVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    draftVoucher = voucherRepository.save(draftVoucher);

    mockMvc
        .perform(
            delete("/api/v1/vouchers/" + draftVoucher.getId())
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getVouchers_unauthorized_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getVouchers_multiColumnSorting_sortsByMultipleColumns() throws Exception {
    // Create vouchers with different dates and statuses for testing multi-column sort
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    
    Voucher voucher1 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", yesterday, "draft");
    Voucher voucher2 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-002", today, "posted");
    Voucher voucher3 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-003", today, "draft");
    Voucher voucher4 = createVoucher(testCompany.getId(), testUser.getId(), "VC2025-004", yesterday, "posted");
    
    voucherRepository.save(voucher1);
    voucherRepository.save(voucher2);
    voucherRepository.save(voucher3);
    voucherRepository.save(voucher4);

    // Sort by date DESC (most recent first), then status ASC (draft before posted)
    // Expected order: voucher2 (today, posted), voucher3 (today, draft), voucher4 (yesterday, posted), voucher1 (yesterday, draft)
    mockMvc
        .perform(
            get("/api/v1/vouchers")
                .param("sort", "voucherDate,desc")
                .param("sort", "status,asc")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.total").value(4))
        .andExpect(jsonPath("$.data[0].voucherNumber").value("VC2025-003")) // today, draft (draft comes before posted)
        .andExpect(jsonPath("$.data[1].voucherNumber").value("VC2025-002")) // today, posted
        .andExpect(jsonPath("$.data[2].voucherNumber").value("VC2025-001")) // yesterday, draft (draft comes before posted)
        .andExpect(jsonPath("$.data[3].voucherNumber").value("VC2025-004")); // yesterday, posted
  }

  private Voucher createVoucher(Long companyId, Long enteredBy, String voucherNumber, String status) {
    return createVoucher(companyId, enteredBy, voucherNumber, LocalDate.now(), status);
  }

  private Voucher createVoucher(
      Long companyId, Long enteredBy, String voucherNumber, LocalDate date, String status) {
    Voucher voucher = new Voucher();
    voucher.setId(UUID.randomUUID());
    voucher.setCompanyId(companyId);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(date);
    voucher.setDescription("Test voucher description");
    voucher.setStatus(status);
    voucher.setCurrency("VND");
    voucher.setTotalDebit(BigDecimal.valueOf(1000));
    voucher.setTotalCredit(BigDecimal.valueOf(1000));
    voucher.setEnteredBy(enteredBy);
    voucher.setCreatedAt(Instant.now());
    voucher.setUpdatedAt(Instant.now());
    return voucher;
  }

  private ChartOfAccount createPostableAccount(String code, String name, Long companyId) {
    ChartOfAccount account = new ChartOfAccount();
    account.setCompanyId(companyId);
    account.setCode(code);
    account.setName(name);
    account.setType("Asset");
    account.setNormalSide("Debit");
    account.setPostable(true);
    account.setOrderingPosition(1);
    return account;
  }

  @Test
  void createVoucher_validRequest_createsVoucherWithLines() throws Exception {
    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();
    Long account2Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(1).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().toString(),
                "description", "Test voucher",
                "lines",
                    List.of(
                        Map.of(
                            "accountId", account1Id,
                            "debit", 1000,
                            "credit", 0,
                            "description", "Cash receipt"),
                        Map.of(
                            "accountId", account2Id,
                            "debit", 0,
                            "credit", 1000,
                            "description", "Revenue"))));

    mockMvc
        .perform(
            post("/api/v1/vouchers")
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.voucherNumber").exists())
        .andExpect(jsonPath("$.data.status").value("draft"))
        .andExpect(jsonPath("$.data.description").value("Test voucher"))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines.length()").value(2));

    // Verify voucher and lines are saved
    List<Voucher> vouchers = voucherRepository.findByCompanyId(testCompany.getId());
    assertEquals(1, vouchers.size());
    Voucher savedVoucher = vouchers.get(0);
    List<VoucherLine> lines =
        voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(savedVoucher.getId());
    assertEquals(2, lines.size());
  }

  @Test
  void createVoucher_unbalanced_returnsValidationErrors() throws Exception {
    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().toString(),
                "description", "Unbalanced voucher",
                "lines",
                    List.of(
                        Map.of(
                            "accountId", account1Id,
                            "debit", 1000,
                            "credit", 0,
                            "description", "Cash receipt"))));

    mockMvc
        .perform(
            post("/api/v1/vouchers")
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateVoucher_draftVoucher_updatesSuccessfully() throws Exception {
    Voucher draftVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    draftVoucher = voucherRepository.save(draftVoucher);

    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();
    Long account2Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(1).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().plusDays(1).toString(),
                "description", "Updated description",
                "lines",
                    List.of(
                        Map.of(
                            "accountId", account1Id,
                            "debit", 2000,
                            "credit", 0,
                            "description", "Updated cash"),
                        Map.of(
                            "accountId", account2Id,
                            "debit", 0,
                            "credit", 2000,
                            "description", "Updated revenue"))));

    mockMvc
        .perform(
            put("/api/v1/vouchers/" + draftVoucher.getId())
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("Updated description"))
        .andExpect(jsonPath("$.data.lines.length()").value(2));
  }

  @Test
  void updateVoucher_postedVoucher_returnsConflict() throws Exception {
    Voucher postedVoucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "posted");
    postedVoucher = voucherRepository.save(postedVoucher);

    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();
    Long account2Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(1).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().toString(),
                "description", "Try to update",
                "lines",
                    List.of(
                        Map.of("accountId", account1Id, "debit", 1000, "credit", 0),
                        Map.of("accountId", account2Id, "debit", 0, "credit", 1000))));

    mockMvc
        .perform(
            put("/api/v1/vouchers/" + postedVoucher.getId())
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isConflict());
  }

  @Test
  void validateVoucher_validRequest_returnsValid() throws Exception {
    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();
    Long account2Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(1).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().toString(),
                "description", "Test validation",
                "lines",
                    List.of(
                        Map.of(
                            "accountId", account1Id,
                            "debit", 1000,
                            "credit", 0,
                            "description", "Cash"),
                        Map.of(
                            "accountId", account2Id,
                            "debit", 0,
                            "credit", 1000,
                            "description", "Revenue"))));

    mockMvc
        .perform(
            post("/api/v1/vouchers/00000000-0000-0000-0000-000000000000/validate")
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  void validateVoucher_unbalanced_returnsValidationErrors() throws Exception {
    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();

    String requestJson =
        objectMapper.writeValueAsString(
            Map.of(
                "date", LocalDate.now().toString(),
                "description", "Unbalanced",
                "lines",
                    List.of(
                        Map.of(
                            "accountId", account1Id,
                            "debit", 1000,
                            "credit", 0,
                            "description", "Cash"))));

    mockMvc
        .perform(
            post("/api/v1/vouchers/00000000-0000-0000-0000-000000000000/validate")
                .contentType(APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.errors").isMap());
  }

  @Test
  void getVoucherById_withLines_returnsVoucherWithLines() throws Exception {
    Voucher voucher =
        createVoucher(testCompany.getId(), testUser.getId(), "VC2025-001", "draft");
    voucher = voucherRepository.save(voucher);

    Long account1Id = chartOfAccountsRepository.findByCompanyId(testCompany.getId()).get(0).getId();
    VoucherLine line1 = new VoucherLine();
    line1.setVoucherId(voucher.getId());
    line1.setLineNumber(1);
    line1.setAccountId(account1Id);
    line1.setDebit(BigDecimal.valueOf(1000));
    line1.setCredit(BigDecimal.ZERO);
    line1.setCompanyId(testCompany.getId());
    voucherLineRepository.save(line1);

    mockMvc
        .perform(
            get("/api/v1/vouchers/" + voucher.getId())
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(voucher.getId().toString()))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines.length()").value(1))
        .andExpect(jsonPath("$.data.lines[0].accountId").value(account1Id));
  }
}

