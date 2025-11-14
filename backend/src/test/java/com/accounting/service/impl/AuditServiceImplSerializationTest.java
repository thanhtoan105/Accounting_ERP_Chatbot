package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import com.accounting.test.IntegrationTest;

/**
 * AC3: Unit tests assert audit records serialize field diffs, actor identity,
 * IP, and user agent.
 */
@SpringBootTest
@Transactional
@Rollback
class AuditServiceImplSerializationTest extends IntegrationTest {

    @Autowired
    private AuditServiceImpl auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    private Company testCompany;
    private User testUser;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        // Create test company
        testCompany = new Company();
        testCompany.setCode("TEST");
        testCompany.setName("Test Company");
        testCompany.setTaxCode("1234567890");
        testCompany.setAddress("Test Address");
        testCompany = companyRepository.save(testCompany);

        CompanyContext.setCompanyId(testCompany.getId());

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("hashed");
        testUser.setRole("admin");
        testUser.setCompanyId(testCompany.getId());
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                testUser.getId().toString(), null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Setup mock request with IP and user agent
        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.100");
        mockRequest.addHeader("User-Agent", "Mozilla/5.0 Test Browser");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void logCustomerCreated_serializesFieldDiffs() {
        Map<String, String> newValues = new HashMap<>();
        newValues.put("name", "Customer One");
        newValues.put("taxCode", "1234567890");

        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                newValues,
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getChanges()).isNotNull();
        JsonNode changesNode = log.getChanges();
        // Changes should contain the new values in "after" object
        assertThat(changesNode.has("after")).isTrue();
        assertThat(changesNode.get("after").has("name")).isTrue();
        assertThat(changesNode.get("after").has("taxCode")).isTrue();
    }

    @Test
    void logCustomerUpdated_serializesFieldDiffs() {
        Map<String, String> oldValues = new HashMap<>();
        oldValues.put("name", "Old Name");
        oldValues.put("address", "Old Address");
        Map<String, String> newValues = new HashMap<>();
        newValues.put("name", "New Name");
        newValues.put("address", "New Address");

        auditService.logCustomerUpdated(
                1L,
                "CUST-001",
                testUser.getId(),
                oldValues,
                newValues,
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getChanges()).isNotNull();
        JsonNode changesNode = log.getChanges();
        // Changes should contain before and after objects
        assertThat(changesNode.has("before")).isTrue();
        assertThat(changesNode.has("after")).isTrue();
        assertThat(changesNode.get("before").has("name")).isTrue();
        assertThat(changesNode.get("after").has("name")).isTrue();
    }

    @Test
    void logCustomerCreated_capturesActorIdentity() {
        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                Map.of("code", "CUST-001"),
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getUserId()).isEqualTo(testUser.getId());
        assertThat(log.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(log.getActorRole()).isEqualTo("admin");
    }

    @Test
    void logCustomerCreated_capturesIpAddress() {
        mockRequest.setRemoteAddr("10.0.0.50");
        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                Map.of("code", "CUST-001"),
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getIpAddress()).isEqualTo("10.0.0.50");
    }

    @Test
    void logCustomerCreated_capturesUserAgent() {
        // Create a new request with custom user agent
        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setRemoteAddr("192.168.1.100");
        customRequest.addHeader("User-Agent", "Custom User Agent String");

        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                Map.of("code", "CUST-001"),
                customRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getUserAgent()).isEqualTo("Custom User Agent String");
    }

    @Test
    void logCustomerCreated_capturesCompanyId() {
        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                Map.of("code", "CUST-001"),
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getCompanyId()).isEqualTo(testCompany.getId());
    }

    @Test
    void logCustomerDeleted_serializesFailureReason() {
        auditService.logCustomerDeleted(
                1L,
                "CUST-001",
                "Record is referenced by vouchers",
                testUser.getId(),
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        // Deletion with reason should be logged
        assertThat(log.getAction()).isEqualTo("CUSTOMER_DELETED");
        assertThat(log.getEntityType()).isEqualTo("CUSTOMER");
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getReason()).contains("id:1");
        assertThat(log.getReason()).contains("code:CUST-001");
    }

    @Test
    void logCustomerCreated_setsSuccessFlag() {
        auditService.logCustomerCreated(
                1L,
                "CUST-001",
                testUser.getId(),
                Map.of("code", "CUST-001"),
                mockRequest);

        AuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getSuccess()).isTrue();
    }
}
