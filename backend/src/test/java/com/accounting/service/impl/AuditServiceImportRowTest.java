package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.accounting.entity.Company;
import com.accounting.entity.ImportAuditEntry;
import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.ImportAuditEntryRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import com.accounting.test.IntegrationTest;

/**
 * AC6: Import handler tests ensure per-row audit records are generated for success and failure paths.
 */
@SpringBootTest
@Transactional
@Rollback
class AuditServiceImportRowTest extends IntegrationTest {

  @Autowired
  private AuditServiceImpl auditService;

  @Autowired
  private ImportAuditEntryRepository importAuditEntryRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private UserRepository userRepository;

  private Company testCompany;
  private User testUser;
  private UUID attemptId;

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

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(testUser.getId().toString(), null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);

    attemptId = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CompanyContext.clear();
  }

  @Test
  void logImportRow_successPath_createsAuditEntry() {
    auditService.logImportRow(
        testCompany.getId(),
        testUser.getId(),
        ImportType.CUSTOMERS,
        attemptId,
        "customers.csv",
        1,
        "SUCCESS",
        null,
        "{\"code\":\"CUST-001\",\"name\":\"Customer One\"}",
        null,
        "192.168.1.100",
        "Test Browser");

    ImportAuditEntry entry = importAuditEntryRepository.findAll().get(0);
    assertThat(entry.getCompanyId()).isEqualTo(testCompany.getId());
    assertThat(entry.getImportType()).isEqualTo("customers");
    assertThat(entry.getAttemptId()).isEqualTo(attemptId);
    assertThat(entry.getSourceFilename()).isEqualTo("customers.csv");
    assertThat(entry.getRowNumber()).isEqualTo(1);
    assertThat(entry.getStatus()).isEqualTo("SUCCESS");
    assertThat(entry.getAfterPayload()).isNotNull();
    assertThat(entry.getAfterPayload()).contains("CUST-001");
    assertThat(entry.getCreatedBy()).isEqualTo(testUser.getId());
    assertThat(entry.getIpAddress()).isEqualTo("192.168.1.100");
    assertThat(entry.getUserAgent()).isEqualTo("Test Browser");
  }

  @Test
  void logImportRow_failurePath_createsAuditEntry() {
    auditService.logImportRow(
        testCompany.getId(),
        testUser.getId(),
        ImportType.SUPPLIERS,
        attemptId,
        "suppliers.csv",
        5,
        "ERROR",
        "{\"code\":\"SUP-005\",\"name\":\"Supplier Five\"}",
        null,
        "Tax code must be exactly 10 digits",
        "10.0.0.50",
        "Custom Agent");

    ImportAuditEntry entry = importAuditEntryRepository.findAll().get(0);
    assertThat(entry.getCompanyId()).isEqualTo(testCompany.getId());
    assertThat(entry.getImportType()).isEqualTo("suppliers");
    assertThat(entry.getAttemptId()).isEqualTo(attemptId);
    assertThat(entry.getSourceFilename()).isEqualTo("suppliers.csv");
    assertThat(entry.getRowNumber()).isEqualTo(5);
    assertThat(entry.getStatus()).isEqualTo("ERROR");
    assertThat(entry.getBeforePayload()).isNotNull();
    assertThat(entry.getBeforePayload()).contains("SUP-005");
    assertThat(entry.getAfterPayload()).isNull();
    assertThat(entry.getMessage()).isEqualTo("Tax code must be exactly 10 digits");
    assertThat(entry.getCreatedBy()).isEqualTo(testUser.getId());
    assertThat(entry.getIpAddress()).isEqualTo("10.0.0.50");
    assertThat(entry.getUserAgent()).isEqualTo("Custom Agent");
  }

  @Test
  void logImportRow_multipleRows_createsMultipleEntries() {
    UUID attemptId1 = UUID.randomUUID();
    
    // Log successful row
    auditService.logImportRow(
        testCompany.getId(),
        testUser.getId(),
        ImportType.CUSTOMERS,
        attemptId1,
        "customers.csv",
        1,
        "SUCCESS",
        null,
        "{\"code\":\"CUST-001\"}",
        null,
        "192.168.1.100",
        "Test Browser");

    // Log failed row
    auditService.logImportRow(
        testCompany.getId(),
        testUser.getId(),
        ImportType.CUSTOMERS,
        attemptId1,
        "customers.csv",
        2,
        "ERROR",
        "{\"code\":\"CUST-002\"}",
        null,
        "Validation error",
        "192.168.1.100",
        "Test Browser");

    // Log another successful row
    auditService.logImportRow(
        testCompany.getId(),
        testUser.getId(),
        ImportType.CUSTOMERS,
        attemptId1,
        "customers.csv",
        3,
        "SUCCESS",
        null,
        "{\"code\":\"CUST-003\"}",
        null,
        "192.168.1.100",
        "Test Browser");

    var entries = importAuditEntryRepository.findByAttemptId(attemptId1);
    assertThat(entries).hasSize(3);
    
    // Verify success entries
    var successEntries = entries.stream()
        .filter(e -> "SUCCESS".equals(e.getStatus()))
        .toList();
    assertThat(successEntries).hasSize(2);
    
    // Verify failure entry
    var failureEntry = entries.stream()
        .filter(e -> "ERROR".equals(e.getStatus()))
        .findFirst()
        .orElse(null);
    assertThat(failureEntry).isNotNull();
    assertThat(failureEntry.getRowNumber()).isEqualTo(2);
    assertThat(failureEntry.getMessage()).isEqualTo("Validation error");
  }
}

