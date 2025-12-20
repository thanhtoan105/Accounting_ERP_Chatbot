package com.accounting.controller.chart;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@SpringBootTest
@AutoConfigureMockMvc
class ChartOfAccountsControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private Validator validator;

  private Company testCompany;
  private User testUser;
  private String testToken;

  @BeforeEach
  void setUp() {
    // Clean up existing data using native SQL to handle FK constraints
    // First, set all parent_id to NULL to break FK relationships
    jdbcTemplate.execute("UPDATE chart_of_accounts SET parent_id = NULL");
    // Then delete all accounts
    jdbcTemplate.execute("DELETE FROM chart_of_accounts");
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
    // Clean up test data using native SQL to handle FK constraints
    jdbcTemplate.execute("UPDATE chart_of_accounts SET parent_id = NULL");
    jdbcTemplate.execute("DELETE FROM chart_of_accounts");
    userRepository.deleteAll();
    companyRepository.deleteAll();
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
    // Without authentication, Spring Security returns 403 Forbidden (not 401)
    mockMvc
        .perform(
            get("/api/v1/chart-of-accounts")
                .header("X-Company-Id", String.valueOf(testCompany.getId())))
        .andExpect(status().isForbidden());
  }

  @Test
  void createAccount_withDuplicateCode_throwsDataIntegrityViolation() {
    // Create first account
    ChartOfAccount account1 = createAccount("1111", "Tiền Việt Nam", null, true);

    // Attempt to create duplicate code for same company
    ChartOfAccount account2 = new ChartOfAccount();
    account2.setCompanyId(testCompany.getId());
    account2.setCode("1111"); // Duplicate code
    account2.setName("Duplicate Account");
    account2.setType("Asset");
    account2.setNormalSide("Debit");
    account2.setPostable(true);
    account2.setOrderingPosition(1111);

    // Should throw DataIntegrityViolationException due to unique constraint
    org.springframework.dao.DataIntegrityViolationException exception = org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> chartOfAccountsRepository.save(account2));

    assertTrue(
        exception.getMessage().contains("ux_chart_of_accounts_company_code")
            || exception.getMessage().contains("duplicate key")
            || exception.getMessage().contains("unique constraint"));
  }

  @Test
  void createAccount_withInvalidCodeFormat_throwsValidationException() {
    // Test non-numeric code
    ChartOfAccount account1 = new ChartOfAccount();
    account1.setCompanyId(testCompany.getId());
    account1.setCode("ABC"); // Invalid: non-numeric
    account1.setName("Invalid Code Account");
    account1.setType("Asset");
    account1.setNormalSide("Debit");
    account1.setPostable(true);
    account1.setOrderingPosition(0);

    // Validate using Validator directly (JPA validation might not trigger on save)
    Set<ConstraintViolation<ChartOfAccount>> violations = validator.validate(account1);
    assertTrue(
        violations.size() > 0,
        "Should have validation violations for invalid code format");
    assertTrue(
        violations.stream()
            .anyMatch(
                violation -> violation.getPropertyPath().toString().equals("code")
                    && (violation.getMessage().contains("numeric")
                        || violation.getMessage().contains("digits"))),
        "Should have code format validation error");
  }

  @Test
  void createAccount_withCodeExceeding4Digits_throwsValidationException() {
    // Test code exceeding 4 digits
    ChartOfAccount account = new ChartOfAccount();
    account.setCompanyId(testCompany.getId());
    account.setCode("12345"); // Invalid: exceeds 4 digits
    account.setName("Invalid Code Account");
    account.setType("Asset");
    account.setNormalSide("Debit");
    account.setPostable(true);
    account.setOrderingPosition(0);

    // Validate using Validator directly
    Set<ConstraintViolation<ChartOfAccount>> violations = validator.validate(account);
    assertTrue(
        violations.size() > 0,
        "Should have validation violations for code exceeding 4 digits");
    assertTrue(
        violations.stream()
            .anyMatch(
                violation -> violation.getPropertyPath().toString().equals("code")
                    && (violation.getMessage().contains("numeric")
                        || violation.getMessage().contains("digits"))),
        "Should have code format validation error");
  }

  @Test
  void createAccount_withInvalidCodeHierarchy_createsOrphanedAccount() {
    // Create parent account
    ChartOfAccount parent = createAccount("131", "Phải thu", null, false);

    // Attempt to create child with code that doesn't start with parent code
    ChartOfAccount invalidChild = new ChartOfAccount();
    invalidChild.setCompanyId(testCompany.getId());
    invalidChild.setCode("1321"); // Invalid: doesn't start with "131"
    invalidChild.setName("Invalid Hierarchy Account");
    invalidChild.setType("Asset");
    invalidChild.setNormalSide("Debit");
    invalidChild.setPostable(true);
    invalidChild.setParentId(parent.getId()); // Set parent but code doesn't match
    invalidChild.setOrderingPosition(1321);

    // Note: This will save successfully at database level (no DB constraint),
    // but violates business rule. Validation should be done at service/application
    // level.
    // For now, we verify the account can be created (validation happens in service
    // layer)
    ChartOfAccount saved = chartOfAccountsRepository.save(invalidChild);
    assertTrue(saved.getId() != null);

    // The validation should be enforced by AccountValidator in service layer
    // when used in voucher picker or edit operations
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
    account.setOrderingPosition(
        Integer.parseInt(code.replaceAll("[^0-9]", "").isEmpty() ? "0" : code.replaceAll("[^0-9]", "")));
    return chartOfAccountsRepository.save(account);
  }
}
