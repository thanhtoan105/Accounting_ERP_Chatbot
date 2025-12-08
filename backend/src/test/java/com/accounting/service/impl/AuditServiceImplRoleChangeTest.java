package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.accounting.entity.AuditLog;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;

import jakarta.servlet.http.HttpServletRequest;

@SpringBootTest
@DirtiesContext
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuditServiceImplRoleChangeTest extends com.accounting.test.IntegrationTest {

  @Autowired private AuditServiceImpl auditService;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Mock private HttpServletRequest httpRequest;

  private User targetUser;
  private Long changedByUserId;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    userRepository.deleteAll();
    CompanyContext.setCompanyId(1L);

    // Create target user
    targetUser = new User();
    targetUser.setEmail("target@example.com");
    targetUser.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
    targetUser.setFullName("Target User");
    targetUser.setRole("accountant");
    targetUser.setCompanyId(1L);
    targetUser.setFailedLoginCount(0);
    targetUser.setCreatedAt(Instant.now());
    targetUser.setUpdatedAt(Instant.now());
    targetUser = userRepository.save(targetUser);

    // Create user who changes the role
    User changerUser = new User();
    changerUser.setEmail("changer@example.com");
    changerUser.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
    changerUser.setFullName("Changer User");
    changerUser.setRole("admin");
    changerUser.setCompanyId(1L);
    changerUser.setFailedLoginCount(0);
    changerUser.setCreatedAt(Instant.now());
    changerUser.setUpdatedAt(Instant.now());
    changerUser = userRepository.save(changerUser);
    changedByUserId = changerUser.getId();
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void logRoleChange_shouldCreateAuditLogWithAllFields() {
    String oldRole = "accountant";
    String newRole = "admin";
    String ipAddress = "127.0.0.1";
    String userAgent = "TestAgent";

    org.mockito.Mockito.when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
    org.mockito.Mockito.when(httpRequest.getHeader("User-Agent")).thenReturn(userAgent);

    auditService.logRoleChange(targetUser, oldRole, newRole, changedByUserId, httpRequest);

    var auditLogs = auditLogRepository.findAll();
    assertEquals(1, auditLogs.size());

    AuditLog log = auditLogs.get(0);
    assertEquals(targetUser.getId(), log.getUserId());
    assertEquals("ROLE_CHANGED", log.getAction());
    assertNotNull(log.getReason());
    assertTrue(log.getReason().contains("old_role:accountant"));
    assertTrue(log.getReason().contains("new_role:admin"));
    assertTrue(log.getReason().contains("changed_by:" + changedByUserId));
    assertEquals(ipAddress, log.getIpAddress());
    assertEquals(userAgent, log.getUserAgent());
    assertNotNull(log.getCreatedAt());
  }
}
