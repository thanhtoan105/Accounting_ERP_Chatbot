package com.accounting.imports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.accounting.dto.ImportResultDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import com.accounting.imports.service.impl.MasterDataImportServiceImpl;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.test.IntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ActiveProfiles("import-performance")
class MasterDataImportPerformanceIT extends IntegrationTest {

  private static final int IMPORT_ROW_COUNT = 1_000;

  @Autowired
  private MasterDataImportFacade importFacade;
  @Autowired
  private MasterDataImportService importService;
  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private CompanyRepository companyRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private AuditLogRepository auditLogRepository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private PlatformTransactionManager transactionManager;

  private Long companyId;
  private Long userId;

  @BeforeEach
  void setUp() {
    companyId = seedCompany();
    CompanyContext.setCompanyId(companyId);
    userId = seedUser(companyId);
    SecurityContextHolder.getContext()
        .setAuthentication(authenticationFor(userId, "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CompanyContext.clear();
    deleteCustomerSequences();
    deleteCustomers();
    deleteAuditLogs();
    deleteUser();
    deleteCompany();
  }

  @Test
  void importCustomers_withThousandRows_completesUnderThirtySeconds() {
    MockMultipartFile file = performanceCsv();
    HttpServletRequest request = httpRequest();

    Instant start = Instant.now();
    ImportResultDTO result = importFacade.process(ImportType.CUSTOMERS, file, "en", request);
    Duration duration = Duration.between(start, Instant.now());
    assertThat(importService).isInstanceOf(MasterDataImportServiceImpl.class);
    List<Customer> customers = customerRepository.findByCompanyId(companyId);

    assertThat(duration).isLessThan(Duration.ofSeconds(30));
    assertThat(result.errorCount())
        .withFailMessage("errors: %s", result.errors())
        .isZero();
    assertThat(customers).hasSize(IMPORT_ROW_COUNT);
    assertThat(result.successCount()).isEqualTo(IMPORT_ROW_COUNT);
  }

  private MockMultipartFile performanceCsv() {
    StringBuilder builder = new StringBuilder();
    builder.append("customer_code,name,tax_code,email,phone,address,active\n");
    for (int i = 1; i <= IMPORT_ROW_COUNT; i++) {
      builder
          .append("PERF-")
          .append(String.format("%04d", i))
          .append(',')
          .append("Performance Customer ")
          .append(String.format("%04d", i))
          .append(',')
          .append(String.format("%010d", 1_000_000_000L + i))
          .append(',')
          .append("perf")
          .append(i)
          .append("@example.test,")
          .append("+8491")
          .append(String.format("%07d", i))
          .append(",")
          .append("\"")
          .append("100 Test Avenue, Ho Chi Minh City")
          .append("\",TRUE\n");
    }
    byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);
    return new MockMultipartFile(
        "file", "customers-performance.csv", "text/csv", content);
  }

  private HttpServletRequest httpRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("User-Agent", "JUnit/Performance");
    return request;
  }

  private Long seedCompany() {
    Company company = new Company();
    company.setCode("PERF-" + UUID.randomUUID().toString().substring(0, 8));
    company.setName("Performance Test Company");
    company.setTaxCode(String.format("%010d", System.nanoTime() % 1_000_000_0000L));
    company.setAddress("123 Test Street, District 1, Ho Chi Minh City");
    company.setContactEmail("contact+perf@example.test");
    company.setContactPhone("+84 28 1234 5678");
    company.setFiscalYearStart(LocalDate.of(2025, 1, 1));
    return companyRepository.save(company).getId();
  }

  private Long seedUser(Long companyId) {
    User user = new User();
    user.setEmail("performance.admin@example.test");
    user.setPasswordHash("secret");
    user.setFullName("Performance Admin");
    user.setRole("admin");
    user.setCompanyId(companyId);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return userRepository.save(user).getId();
  }

  private UsernamePasswordAuthenticationToken authenticationFor(
      Long userId, String role) {
    return new UsernamePasswordAuthenticationToken(
        userId.toString(), "token", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  private void deleteCustomers() {
    if (companyId == null) {
      return;
    }
    List<Customer> customers = customerRepository.findByCompanyId(companyId);
    if (!customers.isEmpty()) {
      customerRepository.deleteAll(customers);
    }
  }

  private void deleteAuditLogs() {
    if (userId == null) {
      return;
    }
    List<AuditLog> logs = auditLogRepository.findAll().stream()
        .filter(log -> userId.equals(log.getUserId()))
        .toList();
    if (!logs.isEmpty()) {
      auditLogRepository.deleteAll(logs);
    }
  }

  protected void deleteCustomerSequences() {
    if (companyId == null) {
      return;
    }
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.executeWithoutResult(
        status -> entityManager
            .createNativeQuery(
                "DELETE FROM customer_code_sequences WHERE company_id = :companyId")
            .setParameter("companyId", companyId)
            .executeUpdate());
  }

  private void deleteUser() {
    if (userId != null) {
      userRepository.findById(userId).ifPresent(userRepository::delete);
      userId = null;
    }
  }

  private void deleteCompany() {
    if (companyId != null) {
      companyRepository.findById(companyId).ifPresent(companyRepository::delete);
      companyId = null;
    }
  }

  @TestConfiguration
  @Profile("import-performance")
  static class RealImportServiceConfig {

    @Bean(name = "stubImportService")
    @Primary
    MasterDataImportService stubImportService(
        MasterDataImportServiceImpl delegate) {
      return delegate;
    }
  }
}
