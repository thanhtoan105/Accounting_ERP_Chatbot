package com.accounting.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.dto.admin.TenantProvisionResponse;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.admin.TenantProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @MockitoBean private TenantProvisioningService tenantProvisioningService;

  private Company testCompany;

  private User superAdminUser;
  private User adminUser;
  private User accountantUser;
  private String superAdminToken;
  private String adminToken;
  private String accountantToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    companyRepository.deleteAll();

    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    superAdminUser =
        createUser("superadmin@example.com", "TestPassword123!", "Super Admin", "super_admin");
    superAdminToken =
        jwtTokenProvider.generateAccessToken(
            superAdminUser.getId(), superAdminUser.getEmail(), superAdminUser.getRole());

    adminUser = createUser("admin@example.com", "TestPassword123!", "Admin User", "admin");
    adminToken =
        jwtTokenProvider.generateAccessToken(
            adminUser.getId(), adminUser.getEmail(), adminUser.getRole());

    accountantUser =
        createUser("accountant@example.com", "TestPassword123!", "Accountant User", "accountant");
    accountantToken =
        jwtTokenProvider.generateAccessToken(
            accountantUser.getId(), accountantUser.getEmail(), accountantUser.getRole());
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  private User createUser(String email, String password, String fullName, String role) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFullName(fullName);
    user.setRole(role);
    user.setStatus("ACTIVE");
    user.setCompanyId(testCompany.getId());
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user);
  }

  @Test
  void provisionTenant_withSuperAdmin_returns201() throws Exception {
    Map<String, Object> request = createValidRequest();

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(
            100L,
            "New Company",
            200L,
            "newadmin@example.com",
            "Tenant provisioned successfully. Invitation sent to newadmin@example.com");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.companyId").value(100))
        .andExpect(jsonPath("$.data.invitationId").value(200))
        .andExpect(jsonPath("$.data.companyName").value("New Company"))
        .andExpect(jsonPath("$.data.adminEmail").value("newadmin@example.com"))
        .andExpect(jsonPath("$.data.message").exists());
  }

  @Test
  void provisionTenant_withoutAuth_returns401() throws Exception {
    Map<String, Object> request = createValidRequest();

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void provisionTenant_withRegularAdmin_returns403() throws Exception {
    Map<String, Object> request = createValidRequest();

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void provisionTenant_withAccountant_returns403() throws Exception {
    Map<String, Object> request = createValidRequest();

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + accountantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void provisionTenant_validatesRequestBody_missingCompanyName() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.remove("companyName");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidTaxCode() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("taxCode", "123");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("10 or 14 digits")));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidTaxCodeFormat() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("taxCode", "12345ABCDE");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_missingAdminEmail() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.remove("adminEmail");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidEmailFormat() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("adminEmail", "not-an-email");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidFiscalYearStart() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("fiscalYearStart", "13-01");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidCurrency() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("currency", "INVALID");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_validatesRequestBody_invalidCoaPreset() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("coaPreset", "INVALID");

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void provisionTenant_returnsTenantDetails() throws Exception {
    Map<String, Object> request = createValidRequest();

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(
            999L,
            "Acme Corporation",
            888L,
            "admin@acme.com",
            "Tenant provisioned successfully. Invitation sent to admin@acme.com");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.companyId").value(999))
        .andExpect(jsonPath("$.data.companyName").value("Acme Corporation"))
        .andExpect(jsonPath("$.data.invitationId").value(888))
        .andExpect(jsonPath("$.data.adminEmail").value("admin@acme.com"))
        .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("successfully")));
  }

  @Test
  void provisionTenant_accepts10DigitTaxCode() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("taxCode", "1234567890");

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(100L, "New Company", 200L, "admin@example.com", "Success");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  void provisionTenant_accepts14DigitTaxCode() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("taxCode", "12345678901234");

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(100L, "New Company", 200L, "admin@example.com", "Success");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  void provisionTenant_acceptsValidCoaPresetTT200() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("coaPreset", "TT200");

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(100L, "New Company", 200L, "admin@example.com", "Success");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  void provisionTenant_acceptsValidCoaPresetTT133() throws Exception {
    Map<String, Object> request = createValidRequest();
    request.put("coaPreset", "TT133");

    TenantProvisionResponse mockResponse =
        new TenantProvisionResponse(100L, "New Company", 200L, "admin@example.com", "Success");

    when(tenantProvisioningService.provisionTenant(
            any(com.accounting.dto.admin.TenantProvisionRequest.class),
            eq(superAdminUser.getId()),
            any(HttpServletRequest.class)))
        .thenReturn(mockResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/tenants")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  private Map<String, Object> createValidRequest() {
    Map<String, Object> request = new HashMap<>();
    request.put("companyName", "New Company");
    request.put("taxCode", "1234567890");
    request.put("address", "123 Main Street");
    request.put("legalRepresentative", "John Doe");
    request.put("fiscalYearStart", "01-01");
    request.put("currency", "VND");
    request.put("adminEmail", "newadmin@example.com");
    request.put("adminName", "New Admin");
    request.put("coaPreset", "TT200");
    return request;
  }
}
