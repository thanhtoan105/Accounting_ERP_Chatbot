package com.accounting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.User;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CompanySettingsControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private CompanySettingsRepository companySettingsRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @Autowired
  private ObjectMapper objectMapper;

  private Company testCompany;
  private User adminUser;
  private String adminToken;
  private CompanySettings testSettings;

  @BeforeEach
  void setUp() {
    // Delete in order to respect foreign key constraints
    companySettingsRepository.deleteAll();
    // Chart of accounts has self-referencing FK, so use native SQL to delete all
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

    // Create admin user
    adminUser = new User();
    adminUser.setEmail("admin@test.com");
    adminUser.setPasswordHash(passwordEncoder.encode("password"));
    adminUser.setFullName("Admin User");
    adminUser.setCompanyId(testCompany.getId());
    adminUser.setRole("admin");
    adminUser.setStatus("ACTIVE");
    adminUser.setCreatedAt(Instant.now());
    adminUser.setUpdatedAt(Instant.now());
    adminUser = userRepository.save(adminUser);

    adminToken = jwtTokenProvider.generateAccessToken(
        adminUser.getId(), adminUser.getEmail(), adminUser.getRole());

    // Create test settings
    testSettings = new CompanySettings();
    testSettings.setCompanyId(testCompany.getId());
    testSettings.setLegalName("Test Legal Name");
    testSettings.setEInvoiceEnabled(false);
    testSettings.setBankReconciliationEnabled(false);
    testSettings.setCreatedAt(Instant.now());
    testSettings.setUpdatedAt(Instant.now());
    testSettings = companySettingsRepository.save(testSettings);
  }

  @AfterEach
  void tearDown() {
    companySettingsRepository.deleteAll();
    userRepository.deleteAll();
    companyRepository.deleteAll();
    CompanyContext.clear();
  }

  @Test
  void getSettings_asAdmin_returnsSettings() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companyId").value(testCompany.getId()))
        .andExpect(jsonPath("$.data.legalName").value("Test Legal Name"));
  }

  @Test
  void getSettings_whenNotExists_createsDefault() throws Exception {
    companySettingsRepository.deleteAll();

    mockMvc
        .perform(
            get("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companyId").value(testCompany.getId()));
  }

  @Test
  void updateSettings_withValidRequest_updatesSuccessfully() throws Exception {
    // Refresh settings to get the latest updatedAt timestamp
    testSettings = companySettingsRepository.findByCompanyId(testCompany.getId()).orElse(testSettings);

    String requestJson = """
        {
          "legalName": "Updated Legal Name",
          "shortName": "Updated",
          "updatedAt": "%s"
        }
        """
        .formatted(testSettings.getUpdatedAt().toString());

    mockMvc
        .perform(
            put("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.legalName").value("Updated Legal Name"))
        .andExpect(jsonPath("$.data.shortName").value("Updated"));
  }

  @Test
  void updateSettings_withStaleUpdatedAt_returns409() throws Exception {
    String requestJson = """
        {
          "legalName": "Updated Legal Name",
          "updatedAt": "2020-01-01T00:00:00Z"
        }
        """;

    mockMvc
        .perform(
            put("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", testCompany.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isConflict());
  }

  @Test
  void updateSettings_withoutAuth_returns403() throws Exception {
    String requestJson = """
        {
          "legalName": "Updated Legal Name"
        }
        """;

    mockMvc
        .perform(
            put("/api/v1/company-settings")
                .header("X-Company-Id", testCompany.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isForbidden()); // Spring Security returns 403 for missing auth
  }

  @Test
  void getSettings_crossCompanyAccess_preventsDataLeakage() throws Exception {
    // Create a second company (Company B)
    Company companyB = new Company();
    companyB.setCode("COMPB");
    companyB.setName("Company B");
    companyB.setTaxCode("9876543210");
    companyB.setAddress("Company B Address");
    companyB = companyRepository.save(companyB);

    // Set CompanyContext to Company B before creating settings
    CompanyContext.setCompanyId(companyB.getId());

    // Create settings for Company B
    CompanySettings settingsB = new CompanySettings();
    settingsB.setCompanyId(companyB.getId());
    settingsB.setLegalName("Company B Legal Name");
    settingsB.setEInvoiceEnabled(false);
    settingsB.setBankReconciliationEnabled(false);
    settingsB.setCreatedAt(Instant.now());
    settingsB.setUpdatedAt(Instant.now());
    companySettingsRepository.save(settingsB);

    // Reset context to Company A for the test
    CompanyContext.setCompanyId(testCompany.getId());

    // Try to access Company A's settings using Company B's context
    // This should return Company B's settings, not Company A's
    mockMvc
        .perform(
            get("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", companyB.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companyId").value(companyB.getId()))
        .andExpect(jsonPath("$.data.legalName").value("Company B Legal Name"))
        // Verify we did NOT get Company A's data
        .andExpect(jsonPath("$.data.legalName").value(org.hamcrest.Matchers.not("Test Legal Name")));
  }

  @Test
  void updateSettings_crossCompanyAccess_preventsDataLeakage() throws Exception {
    // Create a second company (Company B)
    Company companyB = new Company();
    companyB.setCode("COMPB");
    companyB.setName("Company B");
    companyB.setTaxCode("9876543210");
    companyB.setAddress("Company B Address");
    companyB = companyRepository.save(companyB);

    // Set CompanyContext to Company B before creating settings
    CompanyContext.setCompanyId(companyB.getId());

    // Create settings for Company B
    CompanySettings settingsB = new CompanySettings();
    settingsB.setCompanyId(companyB.getId());
    settingsB.setLegalName("Company B Legal Name");
    settingsB.setEInvoiceEnabled(false);
    settingsB.setBankReconciliationEnabled(false);
    settingsB.setCreatedAt(Instant.now());
    settingsB.setUpdatedAt(Instant.now());
    settingsB = companySettingsRepository.save(settingsB);

    // Reset context to Company A for the test
    CompanyContext.setCompanyId(testCompany.getId());

    // Refresh to get latest updatedAt
    settingsB = companySettingsRepository.findByCompanyId(companyB.getId()).orElse(settingsB);

    // Try to update Company A's settings using Company B's context
    // This should update Company B's settings, not Company A's
    String requestJson = """
        {
          "legalName": "Updated Company B Name",
          "updatedAt": "%s"
        }
        """
        .formatted(settingsB.getUpdatedAt().toString());

    mockMvc
        .perform(
            put("/api/v1/company-settings")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Company-Id", companyB.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companyId").value(companyB.getId()))
        .andExpect(jsonPath("$.data.legalName").value("Updated Company B Name"));

    // Verify Company A's settings were NOT modified
    CompanySettings settingsA = companySettingsRepository.findByCompanyId(testCompany.getId()).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("Test Legal Name", settingsA.getLegalName(),
        "Company A's settings should not be modified when updating Company B");
  }
}
