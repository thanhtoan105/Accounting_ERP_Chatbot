package com.accounting.controller.sales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.entity.VatRate;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SalesInvoiceControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SalesInvoiceRepository salesInvoiceRepository;

  @Autowired
  private SalesInvoiceLineRepository salesInvoiceLineRepository;

  @Autowired
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private AccountingPeriodRepository accountingPeriodRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  private Company testCompany;
  private User testUser;
  private Customer testCustomer;
  private ChartOfAccount revenueAccount;
  private String testToken;

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
    testUser.setPasswordHash(passwordEncoder.encode("Password123!"));
    testUser.setFullName("Test User");
    testUser.setRole("accountant");
    testUser.setStatus("ACTIVE");
    testUser.setCompanyId(testCompany.getId());
    testUser.setCreatedAt(Instant.now());
    testUser.setUpdatedAt(Instant.now());
    testUser = userRepository.save(testUser);

    // Generate JWT token
    testToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getEmail(), testUser.getRole());

    // Create test customer
    testCustomer = new Customer();
    testCustomer.setCode("SUP-001");
    testCustomer.setName("Test Customer");
    testCustomer.setTaxCode("9876543210");
    testCustomer.setCompanyId(testCompany.getId());
    testCustomer.setCreatedAt(Instant.now());
    testCustomer.setUpdatedAt(Instant.now());
    testCustomer = customerRepository.save(testCustomer);

    // Create revenue account
    revenueAccount = new ChartOfAccount();
    revenueAccount.setCode("511");
    revenueAccount.setName("Revenue Account");
    revenueAccount.setType("Revenue");
    revenueAccount.setNormalSide("Credit");
    revenueAccount.setOrderingPosition(1);
    revenueAccount.setPostable(true);
    revenueAccount.setCompanyId(testCompany.getId());
    revenueAccount = chartOfAccountsRepository.save(revenueAccount);

    // Create an open accounting period for the test date
    AccountingPeriod testPeriod = new AccountingPeriod();
    testPeriod.setCompanyId(testCompany.getId());
    testPeriod.setFiscalYear(2025);
    testPeriod.setPeriodNumber(1);
    testPeriod.setPeriodName("January 2025");
    testPeriod.setStartDate(LocalDate.of(2025, 1, 1));
    testPeriod.setEndDate(LocalDate.of(2025, 1, 31));
    testPeriod.setStatus(PeriodStatus.OPEN);
    testPeriod.setCreatedAt(Instant.now());
    testPeriod.setUpdatedAt(Instant.now());
    accountingPeriodRepository.save(testPeriod);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void createSalesInvoice_validRequest_returnsCreated() throws Exception {
    String requestBody = """
        {
          "customerId": %d,
          "invoiceNumber": "BILL-001",
          "invoiceDate": "2025-01-15",
          "dueDate": "2025-02-14",
          "reference": "REF-001",
          "description": "Test sales invoice",
          "status": "DRAFT",
          "lines": [
            {
              "accountId": %d,
              "description": "Test line item",
              "quantity": 1.0,
              "unitPrice": 1000.00,
              "amount": 1000.00,
              "vatRate": "ZERO",
              "vatAmount": 0.00
            }
          ]
        }
        """
        .formatted(testCustomer.getId(), revenueAccount.getId());

    mockMvc
        .perform(
            post("/api/v1/ar/sales-invoices")
                .header("Authorization", "Bearer " + testToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
        .andDo(result -> {
          if (result.getResponse().getStatus() != 201) {
            System.err.println("Response status: " + result.getResponse().getStatus());
            System.err.println("Response body: " + result.getResponse().getContentAsString());
          }
        })
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").exists())
        .andExpect(jsonPath("$.data.invoiceNumber").value("BILL-001"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines[0].description").value("Test line item"));
  }

  @Test
  void createSalesInvoice_duplicateInvoiceNumber_returnsValidationError() throws Exception {
    // Create first invoice
    SalesInvoice existingInvoice = new SalesInvoice();
    // Let Hibernate generate the ID
    existingInvoice.setCompanyId(testCompany.getId());
    existingInvoice.setCustomerId(testCustomer.getId());
    existingInvoice.setInvoiceNumber("BILL-001");
    existingInvoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
    existingInvoice.setDueDate(LocalDate.of(2025, 2, 14));
    existingInvoice.setReference("REF-001");
    existingInvoice.setStatus(SalesInvoiceStatus.DRAFT);
    existingInvoice.setTotalAmount(BigDecimal.valueOf(1000));
    existingInvoice.setVatAmount(BigDecimal.ZERO);
    existingInvoice.setCreatedAt(Instant.now());
    existingInvoice.setUpdatedAt(Instant.now());
    existingInvoice.setCreatedById(testUser.getId());
    existingInvoice = salesInvoiceRepository.save(existingInvoice);

    // Try to create duplicate (same invoice number, same customer, same year)
    String requestBody = """
        {
          "customerId": %d,
          "invoiceNumber": "BILL-001",
          "invoiceDate": "2025-01-20",
          "dueDate": "2025-02-19",
          "reference": "REF-002",
          "description": "Duplicate invoice",
          "status": "DRAFT",
          "lines": [
            {
              "accountId": %d,
              "description": "Test line",
              "quantity": 1.0,
              "unitPrice": 1000.00,
              "amount": 1000.00,
              "vatRate": "ZERO",
              "vatAmount": 0.00
            }
          ]
        }
        """
        .formatted(testCustomer.getId(), revenueAccount.getId());

    mockMvc
        .perform(
            post("/api/v1/ar/sales-invoices")
                .header("Authorization", "Bearer " + testToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
        .andDo(result -> {
          if (result.getResponse().getStatus() == 400) {
            System.err.println("Response body: " + result.getResponse().getContentAsString());
          }
        })
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.headerErrors.invoiceNumber").exists());
  }

  @Test
  void getSalesInvoice_existingInvoice_returnsInvoice() throws Exception {
    // Create invoice
    SalesInvoice invoice = new SalesInvoice();
    // Let Hibernate generate the ID
    invoice.setCompanyId(testCompany.getId());
    invoice.setCustomerId(testCustomer.getId());
    invoice.setInvoiceNumber("BILL-002");
    invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
    invoice.setDueDate(LocalDate.of(2025, 2, 14));
    invoice.setReference("REF-002");
    invoice.setStatus(SalesInvoiceStatus.DRAFT);
    invoice.setTotalAmount(BigDecimal.valueOf(1000));
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setCreatedAt(Instant.now());
    invoice.setUpdatedAt(Instant.now());
    invoice.setCreatedById(testUser.getId());
    invoice = salesInvoiceRepository.save(invoice);

    // Create line
    SalesInvoiceLine line = new SalesInvoiceLine();
    // Let Hibernate generate the ID
    line.setSalesInvoiceId(invoice.getId());
    line.setLineNumber(1);
    line.setAccountId(revenueAccount.getId());
    line.setDescription("Test line");
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPrice(BigDecimal.valueOf(1000));
    line.setAmount(BigDecimal.valueOf(1000));
    line.setVatRate(VatRate.ZERO);
    line.setVatAmount(BigDecimal.ZERO);
    line.setCompanyId(testCompany.getId());
    line.setCreatedAt(Instant.now());
    line.setUpdatedAt(Instant.now());
    salesInvoiceLineRepository.save(line);

    mockMvc
        .perform(
            get("/api/v1/ar/sales-invoices/" + invoice.getId())
                .header("Authorization", "Bearer " + testToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(invoice.getId().toString()))
        .andExpect(jsonPath("$.data.invoiceNumber").value("BILL-002"))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines[0].description").value("Test line"));
  }

  @Test
  void updateSalesInvoice_draftInvoice_updatesSuccessfully() throws Exception {
    // Create draft invoice
    SalesInvoice invoice = new SalesInvoice();
    // Let Hibernate generate the ID
    invoice.setCompanyId(testCompany.getId());
    invoice.setCustomerId(testCustomer.getId());
    invoice.setInvoiceNumber("BILL-003");
    invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
    invoice.setDueDate(LocalDate.of(2025, 2, 14));
    invoice.setReference("REF-003");
    invoice.setStatus(SalesInvoiceStatus.DRAFT);
    invoice.setTotalAmount(BigDecimal.valueOf(1000));
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setCreatedAt(Instant.now());
    invoice.setUpdatedAt(Instant.now());
    invoice.setCreatedById(testUser.getId());
    invoice = salesInvoiceRepository.save(invoice);

    String requestBody = """
        {
          "customerId": %d,
          "invoiceNumber": "BILL-003-UPDATED",
          "invoiceDate": "2025-01-15",
          "dueDate": "2025-02-14",
          "reference": "REF-003-UPDATED",
          "description": "Updated description",
          "status": "DRAFT",
          "lines": [
            {
              "accountId": %d,
              "description": "Updated line",
              "quantity": 1.0,
              "unitPrice": 2000.00,
              "amount": 2000.00,
              "vatRate": "ZERO",
              "vatAmount": 0.00
            }
          ]
        }
        """
        .formatted(testCustomer.getId(), revenueAccount.getId());

    mockMvc
        .perform(
            put("/api/v1/ar/sales-invoices/" + invoice.getId())
                .header("Authorization", "Bearer " + testToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.invoiceNumber").value("BILL-003-UPDATED"))
        .andExpect(jsonPath("$.data.description").value("Updated description"));
  }

  @Test
  void deleteSalesInvoice_draftInvoice_deletesSuccessfully() throws Exception {
    // Create draft invoice
    SalesInvoice invoice = new SalesInvoice();
    // Let Hibernate generate the ID
    invoice.setCompanyId(testCompany.getId());
    invoice.setCustomerId(testCustomer.getId());
    invoice.setInvoiceNumber("BILL-004");
    invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));
    invoice.setDueDate(LocalDate.of(2025, 2, 14));
    invoice.setReference("REF-004");
    invoice.setStatus(SalesInvoiceStatus.DRAFT);
    invoice.setTotalAmount(BigDecimal.valueOf(1000));
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setCreatedAt(Instant.now());
    invoice.setUpdatedAt(Instant.now());
    invoice.setCreatedById(testUser.getId());
    invoice = salesInvoiceRepository.save(invoice);

    mockMvc
        .perform(
            delete("/api/v1/ar/sales-invoices/" + invoice.getId())
                .header("Authorization", "Bearer " + testToken)
                .param("reason", "Test deletion"))
        .andExpect(status().isNoContent());

    // Verify invoice is deleted
    assert salesInvoiceRepository.findById(invoice.getId()).isEmpty();
  }

  @Test
  void getDrafts_returnsUserDrafts() throws Exception {
    // Create draft invoices
    SalesInvoice draft1 = new SalesInvoice();
    // Let Hibernate generate the ID
    draft1.setCompanyId(testCompany.getId());
    draft1.setCustomerId(testCustomer.getId());
    draft1.setInvoiceNumber("DRAFT-001");
    draft1.setInvoiceDate(LocalDate.of(2025, 1, 15));
    draft1.setDueDate(LocalDate.of(2025, 2, 14));
    draft1.setReference("REF-D1");
    draft1.setStatus(SalesInvoiceStatus.DRAFT);
    draft1.setTotalAmount(BigDecimal.valueOf(1000));
    draft1.setVatAmount(BigDecimal.ZERO);
    draft1.setCreatedAt(Instant.now());
    draft1.setUpdatedAt(Instant.now());
    draft1.setCreatedById(testUser.getId());
    salesInvoiceRepository.save(draft1);

    mockMvc
        .perform(
            get("/api/v1/ar/sales-invoices/drafts")
                .header("Authorization", "Bearer " + testToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].invoiceNumber").value("DRAFT-001"));
  }
}
