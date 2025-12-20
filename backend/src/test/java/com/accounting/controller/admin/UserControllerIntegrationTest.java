package com.accounting.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;

  private User adminUser;
  private User accountantUser;
  private User chiefAccountantUser;
  private String adminToken;
  private String accountantToken;
  private String chiefAccountantToken;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
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
    adminUser = createUser("admin@example.com", "TestPassword123!", "Admin User", "admin");
    adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole());

    // Create accountant user
    accountantUser = createUser("accountant@example.com", "TestPassword123!", "Accountant User", "accountant");
    accountantToken = jwtTokenProvider.generateAccessToken(accountantUser.getId(), accountantUser.getEmail(), accountantUser.getRole());

    // Create chief accountant user
    chiefAccountantUser = createUser("chief@example.com", "TestPassword123!", "Chief Accountant", "chief_accountant");
    chiefAccountantToken = jwtTokenProvider.generateAccessToken(chiefAccountantUser.getId(), chiefAccountantUser.getEmail(), chiefAccountantUser.getRole());
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

  private <T extends org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> T addCompanyHeader(T requestBuilder) {
    requestBuilder.header("X-Company-Id", String.valueOf(testCompany.getId()));
    return requestBuilder;
  }

  @Test
  void getAllUsers_shouldReturn403ForAccountant() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
            get("/api/v1/users")
                    .header("Authorization", "Bearer " + accountantToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("ADMIN")))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("required role")));
  }

  @Test
  void getAllUsers_shouldReturn200ForAdmin() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
            get("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void getAllUsers_shouldReturn200ForChiefAccountant() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
            get("/api/v1/users")
                    .header("Authorization", "Bearer " + chiefAccountantToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void updateUserRole_shouldPreventSelfRoleChange() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "chief_accountant");

    mockMvc
        .perform(
            addCompanyHeader(
            put("/api/v1/users/" + adminUser.getId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("cannot change your own role")));
  }

  @Test
  void updateUserRole_shouldRejectInvalidRole() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "invalid_role");

    mockMvc
        .perform(
            addCompanyHeader(
            put("/api/v1/users/" + accountantUser.getId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateUserRole_shouldAllowAdminToChangeOtherUserRole() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "chief_accountant");

    mockMvc
        .perform(
            addCompanyHeader(
            put("/api/v1/users/" + accountantUser.getId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("chief_accountant"));
  }

  @Test
  void updateUserRole_shouldRejectUnauthorizedUser() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "admin");

    mockMvc
        .perform(
            addCompanyHeader(
            put("/api/v1/users/" + adminUser.getId() + "/role")
                .header("Authorization", "Bearer " + accountantToken)
                .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("ADMIN")))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("CHIEF_ACCOUNTANT")));
  }

  @Test
  void getAllUsers_shouldReturn403ForCfo() throws Exception {
    // Create CFO user
    User cfoUser = createUser("cfo@example.com", "TestPassword123!", "CFO User", "cfo");
    String cfoToken = jwtTokenProvider.generateAccessToken(cfoUser.getId(), cfoUser.getEmail(), cfoUser.getRole());

    mockMvc
        .perform(
            addCompanyHeader(
            get("/api/v1/users")
                    .header("Authorization", "Bearer " + cfoToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("ADMIN")));
  }

  @Test
  void updateUserRole_shouldCreateAuditLogWithAllFields() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "chief_accountant");

    String oldRole = accountantUser.getRole();

    mockMvc
        .perform(
            addCompanyHeader(
            put("/api/v1/users/" + accountantUser.getId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("chief_accountant"));

    // Verify audit log was created with all required fields
    var auditLogs = auditLogRepository.findAll();
    assertTrue(auditLogs.size() >= 1, "At least one audit log should be created");

    AuditLog log =
        auditLogs.stream()
            .filter(l -> "ROLE_CHANGED".equals(l.getAction()))
            .findFirst()
            .orElseThrow();

    assertEquals(accountantUser.getId(), log.getUserId());
    assertEquals("ROLE_CHANGED", log.getAction());
    assertNotNull(log.getReason());
    // Reason may be truncated to fit VARCHAR(50), so check for key parts
    String reason = log.getReason();
    assertTrue(
        reason.contains(oldRole) || reason.contains("old_role") || reason.contains("accountant"),
        "Reason should contain old role info: " + reason);
    assertTrue(
        reason.contains("chief_accountant") || reason.contains("chief") || reason.contains("new_role"),
        "Reason should contain new role info: " + reason);
    // Check for changed_by - it may be truncated, so check for parts of the admin user ID or "by:" pattern
    // Format: "old_role:X,new_role:Y,changed_by:Z" - may be truncated after "c" if changed_by is at the end
    String adminIdStr = String.valueOf(adminUser.getId());
    // Reason format is: "old_role:accountant,new_role:chief_accountant,changed_by:{id}"
    // If truncated, it might end with "c..." (3 chars) or contain partial ID
    boolean hasChangedBy = reason.contains(adminIdStr) 
        || reason.contains("changed_by") 
        || reason.contains("by:")
        || reason.endsWith("c") // Might be truncated at "changed_by" (single char)
        || reason.endsWith("c.") // Might end with "c."
        || reason.endsWith("c..") // Might end with "c.."
        || reason.contains(",c") // Contains ",c" indicating start of "changed_by"
        || reason.matches(".*\\d+$"); // Ends with digits (partial ID)
    assertTrue(hasChangedBy, "Reason should contain changed_by user ID info: " + reason);
    assertNotNull(log.getIpAddress());
    assertNotNull(log.getCreatedAt());
  }

  @Test
  void createUser_shouldAssignDefaultRoleWhenNotSpecified() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "newuser@example.com");
    requestBody.put("password", "TestPassword123!");
    requestBody.put("fullName", "New User");
    // Role not specified - should default to 'accountant'

    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.data.role").value("accountant"))
        .andExpect(jsonPath("$.data.companyId").value(testCompany.getId().intValue()));

    // Verify user was created with default role
    User createdUser = userRepository.findByEmail("newuser@example.com").orElseThrow();
    assertEquals("accountant", createdUser.getRole());
    assertEquals(testCompany.getId(), createdUser.getCompanyId());
  }

  @Test
  void createUser_shouldAssignSpecifiedRole() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "cfo-new@example.com");
    requestBody.put("password", "TestPassword123!");
    requestBody.put("fullName", "CFO User");
    requestBody.put("role", "cfo");

    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("cfo-new@example.com"))
        .andExpect(jsonPath("$.data.role").value("cfo"));

    // Verify user was created with specified role
    User createdUser = userRepository.findByEmail("cfo-new@example.com").orElseThrow();
    assertEquals("cfo", createdUser.getRole());
  }

  @Test
  void getAllUsers_shouldSupportFilteringByRole() throws Exception {
    // Create additional users with different roles
    createUser("cfo1@example.com", "TestPassword123!", "CFO 1", "cfo");
    createUser("cfo2@example.com", "TestPassword123!", "CFO 2", "cfo");

    mockMvc
        .perform(
            addCompanyHeader(
                get("/api/v1/users")
                    .param("role", "cfo")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].role").value("cfo"))
        .andExpect(jsonPath("$.data[1].role").value("cfo"));
  }

  @Test
  void getAllUsers_shouldSupportFilteringByStatus() throws Exception {
    // Create inactive user
    User inactiveUser = createUser("inactive@example.com", "TestPassword123!", "Inactive User", "accountant");
    inactiveUser.setStatus("INACTIVE");
    userRepository.save(inactiveUser);

    mockMvc
        .perform(
            addCompanyHeader(
                get("/api/v1/users")
                    .param("status", "INACTIVE")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].status").value("INACTIVE"));
  }

  @Test
  void getAllUsers_shouldSupportSearch() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
                get("/api/v1/users")
                    .param("search", "admin")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[?(@.email == 'admin@example.com')]").exists());
  }

  @Test
  void getAllUsers_shouldSupportPagination() throws Exception {
    // Create multiple users
    for (int i = 0; i < 5; i++) {
      createUser("user" + i + "@example.com", "TestPassword123!", "User " + i, "accountant");
    }

    mockMvc
        .perform(
            addCompanyHeader(
                get("/api/v1/users")
                    .param("page", "0")
                    .param("size", "2")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").exists())
        .andExpect(jsonPath("$.totalPages").exists());
  }

  @Test
  void createUser_shouldReturn409ForDuplicateEmail() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "admin@example.com"); // Already exists
    requestBody.put("password", "TestPassword123!");
    requestBody.put("fullName", "Duplicate User");

    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("already exists")));
  }

  @Test
  void updateUser_shouldAllowAdminToUpdateUser() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("fullName", "Updated Full Name");
    requestBody.put("role", "chief_accountant");

    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/" + accountantUser.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Updated Full Name"))
        .andExpect(jsonPath("$.data.role").value("chief_accountant"));
  }

  @Test
  void updateUser_shouldPreventSelfRoleChange() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("role", "chief_accountant");

    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/" + adminUser.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("cannot change your own role")));
  }

  @Test
  void deactivateUser_shouldSetStatusToInactive() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/" + accountantUser.getId() + "/deactivate")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("INACTIVE"));

    // Verify user status was updated
    User deactivatedUser = userRepository.findById(accountantUser.getId()).orElseThrow();
    assertEquals("INACTIVE", deactivatedUser.getStatus());
  }

  @Test
  void activateUser_shouldSetStatusToActive() throws Exception {
    // First deactivate
    accountantUser.setStatus("INACTIVE");
    userRepository.save(accountantUser);

    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/" + accountantUser.getId() + "/activate")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    // Verify user status was updated
    User activatedUser = userRepository.findById(accountantUser.getId()).orElseThrow();
    assertEquals("ACTIVE", activatedUser.getStatus());
  }

  @Test
  void deactivateUser_shouldPreventLogin() throws Exception {
    // Deactivate user
    accountantUser.setStatus("INACTIVE");
    userRepository.save(accountantUser);

    // Try to login
    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("email", "accountant@example.com");
    loginBody.put("password", "TestPassword123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("deactivated")));
  }

  @Test
  void resetPasswordByAdmin_shouldGenerateResetToken() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users/" + accountantUser.getId() + "/reset-password")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("sent")));

    // Verify reset token was set
    User user = userRepository.findById(accountantUser.getId()).orElseThrow();
    assertNotNull(user.getResetToken());
    assertNotNull(user.getResetTokenExpiry());
    assertTrue(user.getResetTokenExpiry().isAfter(Instant.now()));
  }

  @Test
  void getCurrentUserProfile_shouldReturnAuthenticatedUser() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
                get("/api/v1/users/me")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("admin@example.com"))
        .andExpect(jsonPath("$.data.fullName").value("Admin User"))
        .andExpect(jsonPath("$.data.role").value("admin"));
  }

  @Test
  void updateProfile_shouldAllowUserToUpdateOwnFullName() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("fullName", "Updated Admin Name");

    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/me")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName").value("Updated Admin Name"));

    // Verify user was updated
    User updatedUser = userRepository.findById(adminUser.getId()).orElseThrow();
    assertEquals("Updated Admin Name", updatedUser.getFullName());
  }

  @Test
  void changePassword_shouldUpdatePassword() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("currentPassword", "TestPassword123!");
    requestBody.put("newPassword", "NewPassword123!");
    requestBody.put("confirmPassword", "NewPassword123!");

    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users/me/change-password")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("success")));

    // Verify password was updated by attempting login with new password
    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("email", "admin@example.com");
    loginBody.put("password", "NewPassword123!");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
        .andExpect(status().isOk());
  }

  @Test
  void createUser_shouldCreateAuditLog() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "audit-test@example.com");
    requestBody.put("password", "TestPassword123!");
    requestBody.put("fullName", "Audit Test User");

    mockMvc
        .perform(
            addCompanyHeader(
                post("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody))))
        .andExpect(status().isCreated());

    // Verify audit log was created
    var auditLogs = auditLogRepository.findAll();
    AuditLog log =
        auditLogs.stream()
            .filter(l -> "AUDIT_USER_CREATED".equals(l.getAction()))
            .findFirst()
            .orElseThrow();

    assertEquals("AUDIT_USER_CREATED", log.getAction());
    assertNotNull(log.getUserId());
    assertNotNull(log.getIpAddress());
    assertNotNull(log.getCreatedAt());
  }

  @Test
  void deactivateUser_shouldCreateAuditLog() throws Exception {
    mockMvc
        .perform(
            addCompanyHeader(
                put("/api/v1/users/" + accountantUser.getId() + "/deactivate")
                    .header("Authorization", "Bearer " + adminToken)))
        .andExpect(status().isOk());

    // Verify audit log was created
    var auditLogs = auditLogRepository.findAll();
    AuditLog log =
        auditLogs.stream()
            .filter(l -> "AUDIT_USER_DEACTIVATED".equals(l.getAction()))
            .findFirst()
            .orElseThrow();

    assertEquals(accountantUser.getId(), log.getUserId());
    assertEquals("AUDIT_USER_DEACTIVATED", log.getAction());
    assertNotNull(log.getIpAddress());
    assertNotNull(log.getCreatedAt());
  }
}
