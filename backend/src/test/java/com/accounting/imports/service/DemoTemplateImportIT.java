package com.accounting.imports.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.accounting.dto.ImportResultDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.test.IntegrationTest;

import jakarta.servlet.http.HttpServletRequest;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class DemoTemplateImportIT extends IntegrationTest {

  @Autowired private MasterDataImportFacade importFacade;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private BankAccountRepository bankAccountRepository;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;

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
    deleteCustomers();
    deleteSuppliers();
    deleteBankAccounts();
    deleteAuditLogs();
    deleteUser();
    deleteCompany();
  }

  @Test
  void sanctionedTemplates_shouldImportSuccessfully() throws Exception {
    TemplateData customerTemplate = templateData("customers-template.csv");
    
    // CRITICAL: Do NOT read from the file before passing it to importTemplate!
    // MockMultipartFile.getInputStream() can only be read once - if we read it here,
    // the handler won't be able to read it!
    
    ImportResultDTO customerResult =
        importTemplate(customerTemplate, ImportType.CUSTOMERS);
    
    // Debug: Print result if assertion fails
    if (customerResult.successCount() != customerTemplate.expectedRows()) {
      System.err.println("Import result: successCount=" + customerResult.successCount() 
          + ", errorCount=" + customerResult.errorCount() 
          + ", expectedRows=" + customerTemplate.expectedRows());
      if (customerResult.errorCount() > 0 && customerResult.errors() != null) {
        customerResult.errors().forEach(e -> 
            System.err.println("Error: row=" + e.rowNumber() + ", field=" + e.field() + ", message=" + e.message()));
      }
    }
    
    assertThat(customerResult.errorCount()).isZero();
    assertThat(customerResult.successCount()).isEqualTo(customerTemplate.expectedRows());
    assertThat(customerRepository.findByCompanyId(companyId))
        .hasSize(customerTemplate.expectedRows());

    TemplateData supplierTemplate = templateData("suppliers-template.csv");
    ImportResultDTO supplierResult =
        importTemplate(supplierTemplate, ImportType.SUPPLIERS);
    assertThat(supplierResult.errorCount()).isZero();
    assertThat(supplierResult.successCount()).isEqualTo(supplierTemplate.expectedRows());
    assertThat(supplierRepository.findByCompanyId(companyId))
        .hasSize(supplierTemplate.expectedRows());

    TemplateData bankTemplate = templateData("bank-accounts-template.csv");
    ImportResultDTO bankResult =
        importTemplate(bankTemplate, ImportType.BANK_ACCOUNTS);
    assertThat(bankResult.errorCount()).isZero();
    assertThat(bankResult.successCount()).isEqualTo(bankTemplate.expectedRows());
    assertThat(bankAccountRepository.findByCompanyId(companyId))
        .hasSize(bankTemplate.expectedRows());

    TemplateData openingTemplate = templateData("opening-balances-template.csv");
    ImportResultDTO openingResult =
        importTemplate(openingTemplate, ImportType.OPENING_BALANCES);
    assertThat(openingResult.errorCount()).isZero();
    assertThat(openingResult.successCount()).isEqualTo(openingTemplate.expectedRows());
  }

  private TemplateData templateData(String fileName) throws Exception {
    Path moduleRoot = Path.of("").toAbsolutePath().normalize();
    Path projectRoot = moduleRoot.getParent() != null ? moduleRoot.getParent() : moduleRoot;
    Path path =
        projectRoot.resolve(Path.of("docs", "assets", "import-templates", fileName)).normalize();
    
    if (!Files.exists(path)) {
      throw new IllegalStateException("Template file not found: " + path);
    }
    
    // Read file as bytes directly to preserve line endings
    byte[] fileBytes = Files.readAllBytes(path);
    String content = new String(fileBytes, StandardCharsets.UTF_8);
    List<String> lines =
        content.lines().filter(line -> !line.isBlank()).toList();
    int expectedRows = lines.isEmpty() ? 0 : lines.size() - 1;
    
    System.err.println("Template file: " + path);
    System.err.println("File size: " + fileBytes.length + " bytes (raw), " + content.length() + " chars (string)");
    System.err.println("Lines (non-blank): " + lines.size());
    System.err.println("Expected rows: " + expectedRows);
    System.err.println("First 100 chars: " + content.substring(0, Math.min(100, content.length())));
    
    return new TemplateData(
        fileName, fileBytes, expectedRows);
  }

  private ImportResultDTO importTemplate(TemplateData data, ImportType type)
      throws Exception {
    System.err.println("Creating MockMultipartFile: name=" + data.fileName() + ", size=" + data.content().length + " bytes");
    
    // Verify file content before creating MockMultipartFile
    String contentPreview = new String(data.content(), StandardCharsets.UTF_8);
    System.err.println("Content preview (first 200 chars): " + contentPreview.substring(0, Math.min(200, contentPreview.length())));
    System.err.println("Content line count: " + contentPreview.lines().count());
    
    MockMultipartFile file =
        new MockMultipartFile("file", data.fileName(), "text/csv", data.content());
    // CRITICAL: Do NOT call file.isEmpty() or file.getSize() - they might consume the stream!
    System.err.println("MockMultipartFile created: name=" + data.fileName() + ", content length=" + data.content().length + " bytes");
    
    // CRITICAL: Do NOT read from file.getInputStream() or file.getBytes() before passing to importFacade!
    // MockMultipartFile.getInputStream() can only be read once - if we read it here,
    // the handler won't be able to read it!
    
    try {
      ImportResultDTO result = importFacade.process(type, file, "en", httpRequest(type));
      System.err.println("Import completed: successCount=" + result.successCount() + ", errorCount=" + result.errorCount() + ", skippedCount=" + result.skippedCount());
      if (result.errors() != null && !result.errors().isEmpty()) {
        System.err.println("Errors: " + result.errors().size());
        result.errors().forEach(e -> System.err.println("  - Row " + e.rowNumber() + ", " + e.field() + ": " + e.message()));
      }
      return result;
    } catch (Exception e) {
      System.err.println("Import failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      e.printStackTrace();
      throw e;
    }
  }

  private HttpServletRequest httpRequest(ImportType type) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.10.10.10");
    request.addHeader("User-Agent", "JUnit/Demo-" + type.name());
    return request;
  }

  private Long seedCompany() {
    Company company = new Company();
    company.setCode("DEMO-" + UUID.randomUUID().toString().substring(0, 8));
    company.setName("Demo Template Company");
    company.setTaxCode(String.format("%010d", System.nanoTime() % 1_000_000_0000L));
    company.setAddress("456 Demo Street, Da Nang City");
    company.setContactEmail("demo@example.test");
    company.setContactPhone("+84 236 123 4567");
    company.setFiscalYearStart(LocalDate.of(2025, 1, 1));
    return companyRepository.save(company).getId();
  }

  private Long seedUser(Long companyId) {
    User user = new User();
    user.setEmail("demo.admin@example.test");
    user.setPasswordHash("secret");
    user.setFullName("Demo Admin");
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

  private void deleteSuppliers() {
    if (companyId == null) {
      return;
    }
    List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
    if (!suppliers.isEmpty()) {
      supplierRepository.deleteAll(suppliers);
    }
  }

  private void deleteBankAccounts() {
    if (companyId == null) {
      return;
    }
    List<BankAccount> accounts = bankAccountRepository.findByCompanyId(companyId);
    if (!accounts.isEmpty()) {
      bankAccountRepository.deleteAll(accounts);
    }
  }

  private void deleteAuditLogs() {
    if (userId == null) {
      return;
    }
    List<AuditLog> logs =
        auditLogRepository.findAll().stream()
            .filter(log -> userId.equals(log.getUserId()))
            .toList();
    if (!logs.isEmpty()) {
      auditLogRepository.deleteAll(logs);
    }
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

  private record TemplateData(String fileName, byte[] content, int expectedRows) {}
}
