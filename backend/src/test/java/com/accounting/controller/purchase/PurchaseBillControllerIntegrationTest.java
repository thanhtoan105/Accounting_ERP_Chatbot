package com.accounting.controller.purchase;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.entity.VatRate;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PurchaseBillControllerIntegrationTest extends com.accounting.test.IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private PurchaseBillRepository purchaseBillRepository;

  @Autowired private PurchaseBillLineRepository purchaseBillLineRepository;

  @Autowired private ChartOfAccountsRepository chartOfAccountsRepository;

  @Autowired private CompanyRepository companyRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private SupplierRepository supplierRepository;

  @Autowired private AccountingPeriodRepository accountingPeriodRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtTokenProvider jwtTokenProvider;


  private Company testCompany;
  private User testUser;
  private Supplier testSupplier;
  private ChartOfAccount expenseAccount;
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

    // Create test supplier
    testSupplier = new Supplier();
    testSupplier.setCode("SUP-001");
    testSupplier.setName("Test Supplier");
    testSupplier.setTaxCode("9876543210");
    testSupplier.setCompanyId(testCompany.getId());
    testSupplier.setCreatedAt(Instant.now());
    testSupplier.setUpdatedAt(Instant.now());
    testSupplier = supplierRepository.save(testSupplier);

    // Create expense account
    expenseAccount = new ChartOfAccount();
    expenseAccount.setCode("621");
    expenseAccount.setName("Expense Account");
    expenseAccount.setType("Expense");
    expenseAccount.setNormalSide("Debit");
    expenseAccount.setOrderingPosition(1);
    expenseAccount.setPostable(true);
    expenseAccount.setCompanyId(testCompany.getId());
    expenseAccount = chartOfAccountsRepository.save(expenseAccount);

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
  void createPurchaseBill_validRequest_returnsCreated() throws Exception {
    String requestBody =
        """
        {
          "supplierId": %d,
          "billNumber": "BILL-001",
          "billDate": "2025-01-15",
          "dueDate": "2025-02-14",
          "reference": "REF-001",
          "description": "Test purchase bill",
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
            .formatted(testSupplier.getId(), expenseAccount.getId());

    mockMvc
        .perform(
            post("/api/v1/purchase-bills")
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
        .andExpect(jsonPath("$.data.billNumber").value("BILL-001"))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines[0].description").value("Test line item"));
  }

  @Test
  void createPurchaseBill_duplicateBillNumber_returnsValidationError() throws Exception {
    // Create first bill
    PurchaseBill existingBill = new PurchaseBill();
    // Let Hibernate generate the ID
    existingBill.setCompanyId(testCompany.getId());
    existingBill.setSupplierId(testSupplier.getId());
    existingBill.setBillNumber("BILL-001");
    existingBill.setBillDate(LocalDate.of(2025, 1, 15));
    existingBill.setDueDate(LocalDate.of(2025, 2, 14));
    existingBill.setReference("REF-001");
    existingBill.setStatus(PurchaseBillStatus.DRAFT);
    existingBill.setTotalAmount(BigDecimal.valueOf(1000));
    existingBill.setVatAmount(BigDecimal.ZERO);
    existingBill.setCreatedAt(Instant.now());
    existingBill.setUpdatedAt(Instant.now());
    existingBill.setCreatedById(testUser.getId());
    existingBill = purchaseBillRepository.save(existingBill);

    // Try to create duplicate (same bill number, same supplier, same year)
    String requestBody =
        """
        {
          "supplierId": %d,
          "billNumber": "BILL-001",
          "billDate": "2025-01-20",
          "dueDate": "2025-02-19",
          "reference": "REF-002",
          "description": "Duplicate bill",
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
            .formatted(testSupplier.getId(), expenseAccount.getId());

    mockMvc
        .perform(
            post("/api/v1/purchase-bills")
                .header("Authorization", "Bearer " + testToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
        .andDo(result -> {
          if (result.getResponse().getStatus() == 400) {
            System.err.println("Response body: " + result.getResponse().getContentAsString());
          }
        })
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.headerErrors.billNumber").exists());
  }

  @Test
  void getPurchaseBill_existingBill_returnsBill() throws Exception {
    // Create bill
    PurchaseBill bill = new PurchaseBill();
    // Let Hibernate generate the ID
    bill.setCompanyId(testCompany.getId());
    bill.setSupplierId(testSupplier.getId());
    bill.setBillNumber("BILL-002");
    bill.setBillDate(LocalDate.of(2025, 1, 15));
    bill.setDueDate(LocalDate.of(2025, 2, 14));
    bill.setReference("REF-002");
    bill.setStatus(PurchaseBillStatus.DRAFT);
    bill.setTotalAmount(BigDecimal.valueOf(1000));
    bill.setVatAmount(BigDecimal.ZERO);
    bill.setCreatedAt(Instant.now());
    bill.setUpdatedAt(Instant.now());
    bill.setCreatedById(testUser.getId());
    bill = purchaseBillRepository.save(bill);

    // Create line
    PurchaseBillLine line = new PurchaseBillLine();
    // Let Hibernate generate the ID
    line.setPurchaseBillId(bill.getId());
    line.setLineNumber(1);
    line.setAccountId(expenseAccount.getId());
    line.setDescription("Test line");
    line.setQuantity(BigDecimal.ONE);
    line.setUnitPrice(BigDecimal.valueOf(1000));
    line.setAmount(BigDecimal.valueOf(1000));
    line.setVatRate(VatRate.ZERO);
    line.setVatAmount(BigDecimal.ZERO);
    line.setCompanyId(testCompany.getId());
    line.setCreatedAt(Instant.now());
    line.setUpdatedAt(Instant.now());
    purchaseBillLineRepository.save(line);

    mockMvc
        .perform(
            get("/api/v1/purchase-bills/" + bill.getId())
                .header("Authorization", "Bearer " + testToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(bill.getId().toString()))
        .andExpect(jsonPath("$.data.billNumber").value("BILL-002"))
        .andExpect(jsonPath("$.data.lines").isArray())
        .andExpect(jsonPath("$.data.lines[0].description").value("Test line"));
  }

  @Test
  void updatePurchaseBill_draftBill_updatesSuccessfully() throws Exception {
    // Create draft bill
    PurchaseBill bill = new PurchaseBill();
    // Let Hibernate generate the ID
    bill.setCompanyId(testCompany.getId());
    bill.setSupplierId(testSupplier.getId());
    bill.setBillNumber("BILL-003");
    bill.setBillDate(LocalDate.of(2025, 1, 15));
    bill.setDueDate(LocalDate.of(2025, 2, 14));
    bill.setReference("REF-003");
    bill.setStatus(PurchaseBillStatus.DRAFT);
    bill.setTotalAmount(BigDecimal.valueOf(1000));
    bill.setVatAmount(BigDecimal.ZERO);
    bill.setCreatedAt(Instant.now());
    bill.setUpdatedAt(Instant.now());
    bill.setCreatedById(testUser.getId());
    bill = purchaseBillRepository.save(bill);

    String requestBody =
        """
        {
          "supplierId": %d,
          "billNumber": "BILL-003-UPDATED",
          "billDate": "2025-01-15",
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
            .formatted(testSupplier.getId(), expenseAccount.getId());

    mockMvc
        .perform(
            put("/api/v1/purchase-bills/" + bill.getId())
                .header("Authorization", "Bearer " + testToken)
                .contentType(APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.billNumber").value("BILL-003-UPDATED"))
        .andExpect(jsonPath("$.data.description").value("Updated description"));
  }

  @Test
  void deletePurchaseBill_draftBill_deletesSuccessfully() throws Exception {
    // Create draft bill
    PurchaseBill bill = new PurchaseBill();
    // Let Hibernate generate the ID
    bill.setCompanyId(testCompany.getId());
    bill.setSupplierId(testSupplier.getId());
    bill.setBillNumber("BILL-004");
    bill.setBillDate(LocalDate.of(2025, 1, 15));
    bill.setDueDate(LocalDate.of(2025, 2, 14));
    bill.setReference("REF-004");
    bill.setStatus(PurchaseBillStatus.DRAFT);
    bill.setTotalAmount(BigDecimal.valueOf(1000));
    bill.setVatAmount(BigDecimal.ZERO);
    bill.setCreatedAt(Instant.now());
    bill.setUpdatedAt(Instant.now());
    bill.setCreatedById(testUser.getId());
    bill = purchaseBillRepository.save(bill);

    mockMvc
        .perform(
            delete("/api/v1/purchase-bills/" + bill.getId())
                .header("Authorization", "Bearer " + testToken)
                .param("reason", "Test deletion"))
        .andExpect(status().isNoContent());

    // Verify bill is deleted
    assert purchaseBillRepository.findById(bill.getId()).isEmpty();
  }

  @Test
  void getDrafts_returnsUserDrafts() throws Exception {
    // Create draft bills
    PurchaseBill draft1 = new PurchaseBill();
    // Let Hibernate generate the ID
    draft1.setCompanyId(testCompany.getId());
    draft1.setSupplierId(testSupplier.getId());
    draft1.setBillNumber("DRAFT-001");
    draft1.setBillDate(LocalDate.of(2025, 1, 15));
    draft1.setDueDate(LocalDate.of(2025, 2, 14));
    draft1.setReference("REF-D1");
    draft1.setStatus(PurchaseBillStatus.DRAFT);
    draft1.setTotalAmount(BigDecimal.valueOf(1000));
    draft1.setVatAmount(BigDecimal.ZERO);
    draft1.setCreatedAt(Instant.now());
    draft1.setUpdatedAt(Instant.now());
    draft1.setCreatedById(testUser.getId());
    purchaseBillRepository.save(draft1);

    mockMvc
        .perform(
            get("/api/v1/purchase-bills/drafts")
                .header("Authorization", "Bearer " + testToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].billNumber").value("DRAFT-001"));
  }
}
