package com.accounting.controller.chart;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChartOfAccountsControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;
  private User testUser;
  private String testToken;

  @BeforeEach
  void setUp() {
    chartOfAccountsRepository.deleteAll();
    userRepository.deleteAll();
    companyRepository.deleteAll();

    // Create test company
    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    // Create test user (any role can view COA)
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

    testToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getEmail(), testUser.getRole());
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getChartOfAccounts_returnsHierarchicalStructure() throws Exception {
    // Create test accounts
    ChartOfAccount root = createAccount("1", "Tài sản", null, false);
    ChartOfAccount child = createAccount("11", "Tài sản ngắn hạn", root.getId(), false);
    ChartOfAccount leaf = createAccount("1111", "Tiền Việt Nam", child.getId(), true);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].code").value("1"))
        .andExpect(jsonPath("$.data[0].children").isArray())
        .andExpect(jsonPath("$.data[0].children[0].code").value("11"));
  }

  @Test
  void getChartOfAccounts_withFilters_appliesFilters() throws Exception {
    ChartOfAccount postable1 = createAccount("1111", "Tiền Việt Nam", null, true);
    ChartOfAccount postable2 = createAccount("1311", "Phải thu", null, true);
    ChartOfAccount nonPostable = createAccount("111", "Tiền", null, false);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .param("postable", "true")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.code == '1111')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '1311')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '111')]").doesNotExist());
  }

  @Test
  void getChartOfAccounts_withCodePrefix_filterByPrefix() throws Exception {
    ChartOfAccount account1 = createAccount("131", "Phải thu", null, false);
    ChartOfAccount account2 = createAccount("1311", "Phải thu khách hàng", account1.getId(), true);
    ChartOfAccount account3 = createAccount("132", "Phải thu khác", null, false);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .param("codePrefix", "131")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.code == '131')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '1311')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '132')]").doesNotExist());
  }

  @Test
  void getChartOfAccounts_withSearchTerm_searchesByNameOrCode() throws Exception {
    ChartOfAccount account1 = createAccount("1111", "Tiền Việt Nam", null, true);
    ChartOfAccount account2 = createAccount("1311", "Phải thu khách hàng", null, true);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .param("search", "Tiền")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.code == '1111')]").exists());
  }

  @Test
  void getChartOfAccounts_withSearchTerm_searchesByCode() throws Exception {
    ChartOfAccount account1 = createAccount("1111", "Tiền Việt Nam", null, true);
    ChartOfAccount account2 = createAccount("1311", "Phải thu khách hàng", null, true);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .param("search", "1111")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.code == '1111')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '1311')]").doesNotExist());
  }

  @Test
  void getPostableAccounts_returnsOnlyPostableLeaves() throws Exception {
    ChartOfAccount postable1 = createAccount("1111", "Tiền Việt Nam", null, true);
    ChartOfAccount postable2 = createAccount("1311", "Phải thu", null, true);
    ChartOfAccount nonPostable = createAccount("111", "Tiền", null, false);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts/postable")
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.code == '1111')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '1311')]").exists())
        .andExpect(jsonPath("$.data[?(@.code == '111')]").doesNotExist());
  }

  @Test
  void getAccountById_returnsAccountDetails() throws Exception {
    ChartOfAccount account = createAccount("1111", "Tiền Việt Nam", null, true);

    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts/" + account.getId())
                .header("Authorization", "Bearer " + testToken)
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("1111"))
        .andExpect(jsonPath("$.data.name").value("Tiền Việt Nam"))
        .andExpect(jsonPath("$.data.postable").value(true));
  }

  @Test
  void getChartOfAccounts_requiresAuthentication() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isUnauthorized());
  }

  private ChartOfAccount createAccount(String code, String name, Long parentId, boolean postable) {
    ChartOfAccount account = new ChartOfAccount();
    account.setCompanyId(testCompany.getId());
    account.setCode(code);
    account.setName(name);
    account.setType("Asset");
    account.setNormalSide("Debit");
    account.setPostable(postable);
    account.setParentId(parentId);
    account.setOrderingPosition(Integer.parseInt(code.replaceAll("[^0-9]", "").isEmpty() ? "0" : code.replaceAll("[^0-9]", "")));
    return chartOfAccountsRepository.save(account);
  }
}
