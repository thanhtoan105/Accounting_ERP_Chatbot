package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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

import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CustomerService;
import com.accounting.test.IntegrationTest;

/**
 * AC4: Negative tests attempt forbidden modifications and assert blocked events
 * create audit entries.
 */
@SpringBootTest
@Transactional
@Rollback
class AuditServiceNegativeTest extends IntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

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

        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.168.1.100");
        mockRequest.addHeader("User-Agent", "Test Browser");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void deleteCustomer_blockedDeletion_createsFailedAuditEntry() {
        // Create a customer
        Customer customer = new Customer();
        customer.setCompanyId(testCompany.getId());
        customer.setCode("CUST-001");
        customer.setName("Customer One");
        customer.setTaxCode("1234567890");
        customer.setActive(true);
        customer = customerRepository.save(customer);

        // Attempt to delete the customer (currently always blocked in MVP)
        long auditLogCountBefore = auditLogRepository.count();

        try {
            customerService.delete(customer.getId());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            // Expected to fail - deletion is blocked in MVP
            assertThat(e.getStatusCode().value()).isEqualTo(409); // CONFLICT
        }

        // Verify audit log was created for the blocked attempt
        long auditLogCountAfter = auditLogRepository.count();
        assertThat(auditLogCountAfter).isGreaterThan(auditLogCountBefore);

        // Find the failed deletion audit log
        AuditLog failedLog = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction().equals("CUSTOMER_DELETED") && !log.getSuccess())
                .findFirst()
                .orElse(null);

        assertThat(failedLog).isNotNull();
        assertThat(failedLog.getSuccess()).isFalse();
        assertThat(failedLog.getEntityType()).isEqualTo("CUSTOMER");
        assertThat(failedLog.getFailureReason()).isNotNull();
        assertThat(failedLog.getFailureReason()).contains("blocked");
        assertThat(failedLog.getUserId()).isEqualTo(testUser.getId());
        assertThat(failedLog.getEmail()).isEqualTo(testUser.getEmail());
    }
}
