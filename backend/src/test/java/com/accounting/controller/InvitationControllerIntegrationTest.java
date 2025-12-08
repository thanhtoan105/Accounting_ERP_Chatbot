package com.accounting.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.Invitation;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.InvitationRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
class InvitationControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private InvitationRepository invitationRepository;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @MockitoBean private EmailService emailService;

  private User adminUser;
  private User accountantUser;
  private Company testCompany;
  private String adminToken;
  private String accountantToken;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    invitationRepository.deleteAll(); // Delete invitations first to avoid foreign key constraint
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
    adminUser = createUser("admin@example.com", "TestPassword123!", "Admin User", "admin", testCompany.getId());
    adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole());

    // Create accountant user
    accountantUser = createUser("accountant@example.com", "TestPassword123!", "Accountant User", "accountant", testCompany.getId());
    accountantToken = jwtTokenProvider.generateAccessToken(accountantUser.getId(), accountantUser.getEmail(), accountantUser.getRole());
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  private User createUser(String email, String password, String fullName, String role, Long companyId) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFullName(fullName);
    user.setRole(role);
    user.setCompanyId(companyId);
    user.setFailedLoginCount(0);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user);
  }

  @Test
  void createInvitation_shouldReturn403ForAccountant() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "newuser@example.com");
    requestBody.put("role", "accountant");

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .header("Authorization", "Bearer " + accountantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void createInvitation_shouldReturn200ForAdmin() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "newuser@example.com");
    requestBody.put("role", "accountant");

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.invitationToken").exists())
        .andExpect(jsonPath("$.data.expiresAt").exists());
  }

  @Test
  void createInvitation_shouldRejectDuplicateInvitation() throws Exception {
    // Create first invitation
    Invitation existingInvitation = new Invitation();
    existingInvitation.setEmail("newuser@example.com");
    existingInvitation.setToken("existing-token");
    existingInvitation.setCompanyId(testCompany.getId());
    existingInvitation.setCreatedBy(adminUser.getId());
    existingInvitation.setRole("accountant");
    existingInvitation.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
    existingInvitation.setStatus("PENDING");
    existingInvitation.setCreatedAt(Instant.now());
    existingInvitation.setUpdatedAt(Instant.now());
    invitationRepository.save(existingInvitation);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "newuser@example.com");
    requestBody.put("role", "accountant");

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("pending invitation")));
  }

  @Test
  void getInvitation_shouldReturnInvitationDetailsForValidToken() throws Exception {
    Invitation invitation = new Invitation();
    invitation.setEmail("invitee@example.com");
    invitation.setToken("test-token-12345");
    invitation.setCompanyId(testCompany.getId());
    invitation.setCreatedBy(adminUser.getId());
    invitation.setRole("accountant");
    invitation.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());
    invitation = invitationRepository.save(invitation);

    mockMvc
        .perform(
            get("/api/v1/invitations/" + invitation.getToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("invitee@example.com"))
        .andExpect(jsonPath("$.data.role").value("accountant"))
        .andExpect(jsonPath("$.data.companyName").exists());
  }

  @Test
  void acceptInvitation_shouldCreateUserAccount() throws Exception {
    Invitation invitation = new Invitation();
    invitation.setEmail("newuser@example.com");
    invitation.setToken("accept-token-12345");
    invitation.setCompanyId(testCompany.getId());
    invitation.setCreatedBy(adminUser.getId());
    invitation.setRole("accountant");
    invitation.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());
    invitation = invitationRepository.save(invitation);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("password", "TestPassword123!");
    requestBody.put("confirmPassword", "TestPassword123!");
    requestBody.put("fullName", "New User");

    mockMvc
        .perform(
            post("/api/v1/invitations/" + invitation.getToken() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").exists())
        .andExpect(jsonPath("$.data.user.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.data.user.role").value("accountant"));

    // Verify invitation is marked as accepted
    Invitation updatedInvitation = invitationRepository.findByToken(invitation.getToken()).orElseThrow();
    assertEquals("ACCEPTED", updatedInvitation.getStatus());
  }

  @Test
  void acceptInvitation_shouldRejectExpiredToken() throws Exception {
    Invitation invitation = new Invitation();
    invitation.setEmail("expired@example.com");
    invitation.setToken("expired-token");
    invitation.setCompanyId(testCompany.getId());
    invitation.setCreatedBy(adminUser.getId());
    invitation.setRole("accountant");
    invitation.setExpiresAt(Instant.now().minusSeconds(86400)); // Expired
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());
    invitation = invitationRepository.save(invitation);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("password", "TestPassword123!");
    requestBody.put("confirmPassword", "TestPassword123!");
    requestBody.put("fullName", "New User");

    mockMvc
        .perform(
            post("/api/v1/invitations/" + invitation.getToken() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isNotFound());
  }

  @Test
  void createInvitation_shouldLogInvitationCreated() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "audittest@example.com");
    requestBody.put("role", "accountant");

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isCreated());

    // Verify audit log was created
    var auditLogs = auditLogRepository.findAll();
    assertTrue(auditLogs.size() >= 1, "At least one audit log should be created");

    AuditLog log =
        auditLogs.stream()
            .filter(l -> "INVITATION_CREATED".equals(l.getAction()))
            .findFirst()
            .orElseThrow();

    assertEquals(adminUser.getId(), log.getUserId());
    assertEquals("audittest@example.com", log.getEmail());
    assertEquals("INVITATION_CREATED", log.getAction());
    assertNotNull(log.getReason());
    assertTrue(log.getReason().contains("inviter:" + adminUser.getId()));
    assertTrue(log.getReason().contains("invitee:audittest@example.com"));
    assertTrue(log.getReason().contains("company:" + testCompany.getId()));
    assertTrue(log.getReason().contains("role:accountant"));
    assertTrue(log.getReason().contains("status:PENDING"));
    assertNotNull(log.getIpAddress());
    assertNotNull(log.getCreatedAt());
  }

  @Test
  void acceptInvitation_shouldLogInvitationAccepted() throws Exception {
    Invitation invitation = new Invitation();
    invitation.setEmail("accepttest@example.com");
    invitation.setToken("accept-audit-token");
    invitation.setCompanyId(testCompany.getId());
    invitation.setCreatedBy(adminUser.getId());
    invitation.setRole("accountant");
    invitation.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
    invitation.setStatus("PENDING");
    invitation.setCreatedAt(Instant.now());
    invitation.setUpdatedAt(Instant.now());
    invitation = invitationRepository.save(invitation);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("password", "TestPassword123!");
    requestBody.put("confirmPassword", "TestPassword123!");
    requestBody.put("fullName", "Accept Test User");

    mockMvc
        .perform(
            post("/api/v1/invitations/" + invitation.getToken() + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isOk());

    // Verify audit log was created for invitation acceptance
    var auditLogs = auditLogRepository.findAll();
    assertTrue(auditLogs.size() >= 1, "At least one audit log should be created");

    AuditLog log =
        auditLogs.stream()
            .filter(l -> "INVITATION_ACCEPTED".equals(l.getAction()))
            .findFirst()
            .orElseThrow();

    assertEquals(adminUser.getId(), log.getUserId());
    assertEquals("accepttest@example.com", log.getEmail());
    assertEquals("INVITATION_ACCEPTED", log.getAction());
    assertNotNull(log.getReason());
    assertTrue(log.getReason().contains("invitation_id:" + invitation.getId()));
    assertTrue(log.getReason().contains("inviter:" + adminUser.getId()));
    assertTrue(log.getReason().contains("invitee:accepttest@example.com"));
    assertTrue(log.getReason().contains("company:" + testCompany.getId()));
    assertTrue(log.getReason().contains("status:ACCEPTED"));
    assertNotNull(log.getIpAddress());
    assertNotNull(log.getCreatedAt());
  }

  @Test
  void createInvitation_shouldCallEmailService() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("email", "emailtest@example.com");
    requestBody.put("role", "accountant");

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.invitationToken").exists())
        .andExpect(jsonPath("$.data.expiresAt").exists());

    // Verify email service was called
    ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> inviterCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> companyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);

    org.mockito.Mockito.verify(emailService, org.mockito.Mockito.atLeastOnce())
        .sendInvitationEmail(
            emailCaptor.capture(),
            tokenCaptor.capture(),
            inviterCaptor.capture(),
            companyCaptor.capture(),
            roleCaptor.capture(),
            org.mockito.ArgumentMatchers.any(java.time.Instant.class),
            org.mockito.ArgumentMatchers.any(jakarta.servlet.http.HttpServletRequest.class));

    assertEquals("emailtest@example.com", emailCaptor.getValue());
    assertNotNull(tokenCaptor.getValue());
    assertTrue(tokenCaptor.getValue().length() >= 32, "Token should be at least 32 characters");
    assertEquals("Test Company", companyCaptor.getValue());
    assertEquals("accountant", roleCaptor.getValue());
  }
}
