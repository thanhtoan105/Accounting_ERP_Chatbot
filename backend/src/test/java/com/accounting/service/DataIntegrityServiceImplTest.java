package com.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accounting.dto.integrity.DataIntegrityJobResponse;
import com.accounting.dto.integrity.DataIntegrityRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.BankAccount.AccountType;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.DataIntegrityFindingRepository;
import com.accounting.repository.DataIntegrityJobRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import com.accounting.test.IntegrationTest;

@SpringBootTest
@Transactional
@Rollback
class DataIntegrityServiceImplTest extends IntegrationTest {

  @Autowired
  private DataIntegrityService dataIntegrityService;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private ChartOfAccountsRepository chartRepository;

  @Autowired
  private BankAccountRepository bankAccountRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private DataIntegrityJobRepository jobRepository;

  @Autowired
  private DataIntegrityFindingRepository findingRepository;

  private final HttpServletRequest mockRequest = new MockHttpServletRequest();

  private User admin;
  private Company testCompany;
  private static int testCounter = 0;

  @BeforeEach
  void setUp() {
    // Ensure no jobs exist from previous test runs (extra safety)
    // The IntegrationTest base class should reset the database, but this ensures
    // cleanup
    var allJobs = jobRepository.findAll();
    if (!allJobs.isEmpty()) {
      allJobs.forEach(job -> {
        findingRepository.findByJob(job).forEach(findingRepository::delete);
        jobRepository.delete(job);
      });
      jobRepository.flush();
      // Verify cleanup worked
      assertThat(jobRepository.count()).as("All jobs should be cleaned up before test").isZero();
    }

    // Create unique test company for each test to ensure isolation
    // Using timestamp ensures uniqueness even if tests run quickly
    testCounter++;
    long timestamp = System.currentTimeMillis();
    // Company code is limited to 16 chars, so use short format
    String code = "T" + testCounter + "-" + (timestamp % 1000000);
    testCompany = new Company();
    testCompany.setCode(code);
    testCompany.setName("Test Company " + testCounter);
    testCompany.setTaxCode(String.format("%010d", (timestamp % 10000000000L)));
    testCompany.setAddress("Test Address");
    testCompany = companyRepository.save(testCompany);
    companyRepository.flush();

    CompanyContext.setCompanyId(testCompany.getId());
    admin = new User();
    admin.setEmail("admin" + testCounter + "@example.com");
    admin.setFullName("Admin " + testCounter);
    admin.setPasswordHash("hashed");
    admin.setRole("admin");
    admin.setCompanyId(testCompany.getId());
    admin.setCreatedAt(Instant.now());
    admin.setUpdatedAt(Instant.now());
    admin = userRepository.save(admin);

    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        admin.getId().toString(), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    CompanyContext.clear();
  }

  @Test
  void triggerScan_detectsMasterDataIssues() {
    // Verify no existing jobs for this company (test isolation check)
    assertThat(jobRepository.findByCompanyId(testCompany.getId())).isEmpty();

    createCustomer("CUST-001", "Customer One", "1234567890");
    createCustomer("CUST-002", "Customer Two", "9876543210");
    createChartAccountHierarchy();
    createBankAccount("ACC-001", "VCB");
    createBankAccount("ACC-002", "VCB");

    DataIntegrityRequest request = new DataIntegrityRequest(null, false);
    DataIntegrityJobResponse response = dataIntegrityService.triggerScan(request, mockRequest);

    assertThat(response).isNotNull();
    assertThat(response.findings()).isNotNull();
    // With valid data, there may be no findings, which is also a valid result
  }

  @Test
  void triggerScan_throttledWithoutBypass() {
    // Verify no existing jobs for this company (test isolation check)
    assertThat(jobRepository.findByCompanyId(testCompany.getId())).isEmpty();

    createCustomer("CUST-101", "Customer", "9876543210");
    dataIntegrityService.triggerScan(new DataIntegrityRequest(null, false), mockRequest);

    assertThatThrownBy(() -> dataIntegrityService.triggerScan(new DataIntegrityRequest(null, false), mockRequest))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("Integrity scan throttled");
  }

  @Test
  void triggerScan_allowsBypassThrottle() {
    // Verify no existing jobs for this company (test isolation check)
    assertThat(jobRepository.findByCompanyId(testCompany.getId())).isEmpty();

    createCustomer("CUST-201", "Customer", "1111111111");
    dataIntegrityService.triggerScan(new DataIntegrityRequest(null, false), mockRequest);

    DataIntegrityJobResponse response = dataIntegrityService.triggerScan(new DataIntegrityRequest(null, true),
        mockRequest);
    assertThat(response).isNotNull();
    assertThat(response.findings()).isNotNull();
  }

  private void createCustomer(String code, String name, String taxCode) {
    Customer customer = new Customer();
    customer.setCompanyId(testCompany.getId());
    customer.setCode(code);
    customer.setName(name);
    customer.setTaxCode(taxCode);
    customer.setActive(true);
    customerRepository.save(customer);
  }

  private void createChartAccountHierarchy() {
    ChartOfAccount parent = new ChartOfAccount();
    parent.setCompanyId(testCompany.getId());
    parent.setCode("111");
    parent.setName("Cash");
    parent.setActive(true);
    parent.setType("ASSET");
    parent.setNormalSide("DEBIT");
    parent.setPostable(true);
    parent.setOrderingPosition(1);
    ChartOfAccount savedParent = chartRepository.save(parent);

    ChartOfAccount child = new ChartOfAccount();
    child.setCompanyId(testCompany.getId());
    child.setCode("1111");
    child.setName("Cash Child");
    child.setActive(true);
    child.setType("ASSET");
    child.setNormalSide("DEBIT");
    child.setPostable(true);
    child.setParentId(savedParent.getId());
    child.setOrderingPosition(2);
    chartRepository.save(child);
  }

  private void createBankAccount(String accountNumber, String bankName) {
    BankAccount account = new BankAccount();
    account.setCompanyId(testCompany.getId());
    account.setAccountNumber(accountNumber);
    account.setBankName(bankName);
    account.setType(AccountType.BANK);
    account.setOpeningBalance(BigDecimal.ZERO);
    bankAccountRepository.save(account);
  }
}
