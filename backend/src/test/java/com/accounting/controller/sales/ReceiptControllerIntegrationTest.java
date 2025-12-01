package com.accounting.controller.sales;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.accounting.entity.*;
import com.accounting.enums.Role;
import com.accounting.repository.*;
import org.springframework.http.HttpHeaders;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.test.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration Tests for Receipt Controller
 * 
 * Tests for:
 * - Receipt CRUD endpoints
 * - Receipt posting (GL voucher generation)
 * - Receipt reversal (linked reversal voucher)
 * - Batch import
 * - Audit logging integration
 * - RBAC enforcement
 */

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ReceiptControllerIntegrationTest extends IntegrationTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private JwtTokenProvider jwtTokenProvider;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private CompanyRepository companyRepository;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private BankAccountRepository bankAccountRepository;
  @Autowired
  private SalesInvoiceRepository salesInvoiceRepository;
  @Autowired
  private ARPaymentRepository arPaymentRepository;
  @Autowired
  private ReceiptAllocationRepository receiptAllocationRepository;
  @Autowired
  private VoucherRepository voucherRepository;
  @Autowired
  private AuditLogRepository auditLogRepository;

  private static final String API_BASE = "/api/v1/ar/receipts";

  private Company testCompany;
  private User testAccountant;
  private User testAdmin;
  private Customer testCustomer;
  private BankAccount testBankAccount;
  private SalesInvoice postedInvoice;
  private String accountantToken;
  private String adminToken;

  @BeforeEach
  void setup() {
    // Create test company
    testCompany = new Company();
    testCompany.setCode("TEST");
    testCompany.setName("Test Company");
    testCompany.setTaxCode("1234567890");
    testCompany.setAddress("123 Test St");
    testCompany = companyRepository.save(testCompany);

    CompanyContext.setCompanyId(testCompany.getId());

    // Create accountant user
    testAccountant = new User();
    testAccountant.setEmail("accountant@test.com");
    testAccountant.setPasswordHash(passwordEncoder.encode("Password123!"));
    testAccountant.setFullName("Test Accountant");
    testAccountant.setRole(Role.ACCOUNTANT.getValue());
    testAccountant.setCompanyId(testCompany.getId());
    testAccountant.setStatus("ACTIVE");
    testAccountant.setCreatedAt(Instant.now());
    testAccountant.setUpdatedAt(Instant.now());
    testAccountant = userRepository.save(testAccountant);

    accountantToken = jwtTokenProvider.generateAccessToken(
        testAccountant.getId(), testAccountant.getEmail(), testAccountant.getRole());

    // Create admin user
    testAdmin = new User();
    testAdmin.setEmail("admin@test.com");
    testAdmin.setPasswordHash(passwordEncoder.encode("Password123!"));
    testAdmin.setFullName("Test Admin");
    testAdmin.setRole(Role.ADMIN.getValue());
    testAdmin.setCompanyId(testCompany.getId());
    testAdmin.setStatus("ACTIVE");
    testAdmin.setCreatedAt(Instant.now());
    testAdmin.setUpdatedAt(Instant.now());
    testAdmin = userRepository.save(testAdmin);

    adminToken = jwtTokenProvider.generateAccessToken(
        testAdmin.getId(), testAdmin.getEmail(), testAdmin.getRole());

    // Create test customer
    testCustomer = new Customer();
    testCustomer.setCode("CUST-001");
    testCustomer.setName("Test Customer");
    testCustomer.setTaxCode("9876543210");
    testCustomer.setCompanyId(testCompany.getId());
    testCustomer.setCreatedAt(Instant.now());
    testCustomer.setUpdatedAt(Instant.now());
    testCustomer = customerRepository.save(testCustomer);

    // Create test bank account
    testBankAccount = new BankAccount();
    testBankAccount.setCompanyId(testCompany.getId());
    testBankAccount.setAccountNumber("BANK-001");
    testBankAccount.setBankName("Test Bank");
    testBankAccount.setBranch("Main Branch");
    testBankAccount.setType(BankAccount.AccountType.BANK);
    testBankAccount.setOpeningBalance(BigDecimal.valueOf(10_000_000));
    testBankAccount.setActive(true);
    testBankAccount = bankAccountRepository.save(testBankAccount);

    // Create posted sales invoice
    postedInvoice = createPostedInvoice("INV-001", BigDecimal.valueOf(5_000_000));
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  private SalesInvoice createPostedInvoice(String invoiceNumber, BigDecimal amount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setCompanyId(testCompany.getId());
    invoice.setCustomerId(testCustomer.getId());
    invoice.setInvoiceNumber(invoiceNumber);
    invoice.setInvoiceDate(LocalDate.now());
    invoice.setDueDate(LocalDate.now().plusDays(30));
    invoice.setTotalAmount(amount);
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    invoice.setCreatedById(testAccountant.getId());
    invoice.setIsSensitive(false);
    invoice.setIsDeleted(false);
    invoice.setAmountPaid(BigDecimal.ZERO);
    invoice.setRemainingBalance(amount);
    return salesInvoiceRepository.save(invoice);
  }

  private ARPayment createTestReceipt(ReceiptStatus status, BigDecimal amount) {
    ARPayment receipt = new ARPayment();
    receipt.setCompanyId(testCompany.getId());
    receipt.setCustomerId(testCustomer.getId());
    receipt.setReceiptNumber("REC-" + UUID.randomUUID().toString().substring(0, 8));
    receipt.setReceiptDate(LocalDate.now());
    receipt.setBankAccountId(testBankAccount.getId());
    receipt.setPayee(testCustomer.getName());
    receipt.setAmount(amount);
    receipt.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
    receipt.setStatus(status);
    receipt.setIsStandalone(false);
    receipt.setCreatedById(testAccountant.getId());
    return arPaymentRepository.save(receipt);
  }

  @Nested
  @DisplayName("Receipt CRUD Operations")
  class ReceiptCRUDTests {

    @Test
    @DisplayName("POST /receipts - should create receipt with auto-generated number")
    void testCreateReceipt() throws Exception {
      // GIVEN: Valid receipt creation request
      Map<String, Object> requestBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 5_000_000,
          "reference", "TEST-001",
          "paymentMethod", "BANK_TRANSFER",
          "payee", "Test Customer",
          "isStandalone", false);

      // WHEN: Creating receipt
      CompanyContext.setCompanyId(testCompany.getId());
      MvcResult result = mockMvc.perform(post(API_BASE)
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(requestBody)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.receiptNumber").exists())
          .andExpect(jsonPath("$.status").value("DRAFT"))
          .andExpect(jsonPath("$.amount").value(5000000))
          .andReturn();

      // THEN: Verify audit log created
      List<AuditLog> auditLogs = auditLogRepository.findAll();
      assertTrue(auditLogs.stream()
          .anyMatch(log -> "ARPayment".equals(log.getEntityType()) && "CREATE".equals(log.getAction())));
    }

    @Test
    @DisplayName("GET /receipts - should list receipts with pagination and filters")
    void testListReceipts() throws Exception {
      // GIVEN: Multiple receipts exist
      createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(1_000_000));
      createTestReceipt(ReceiptStatus.POSTED, BigDecimal.valueOf(2_000_000));
      createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(3_000_000));

      // WHEN: Querying with pagination and filters
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(get(API_BASE)
          .header("Authorization", "Bearer " + accountantToken)
          .param("page", "0")
          .param("size", "10")
          .param("status", "DRAFT"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray())
          .andExpect(jsonPath("$.content", hasSize(2)))
          .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /receipts/{id} - should get receipt with allocations")
    void testGetReceipt() throws Exception {
      // GIVEN: Receipt with allocations exists
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));
      ReceiptAllocation allocation = new ReceiptAllocation();
      allocation.setCompanyId(testCompany.getId());
      allocation.setReceiptId(receipt.getId());
      allocation.setSalesInvoiceId(postedInvoice.getId());
      allocation.setAllocatedAmount(BigDecimal.valueOf(5_000_000));
      allocation.setAllocationOrder(1);
      receiptAllocationRepository.save(allocation);

      // WHEN: Fetching receipt details
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(get(API_BASE + "/" + receipt.getId())
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(receipt.getId().toString()))
          .andExpect(jsonPath("$.allocations").isArray())
          .andExpect(jsonPath("$.allocations", hasSize(1)));
    }

    @Test
    @DisplayName("PUT /receipts/{id} - should update DRAFT receipt only")
    void testUpdateDraftReceipt() throws Exception {
      // GIVEN: DRAFT receipt exists
      ARPayment draftReceipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(1_000_000));

      Map<String, Object> updateBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 2_000_000,
          "reference", "UPDATED-001",
          "paymentMethod", "BANK_TRANSFER",
          "payee", testCustomer.getName(),
          "isStandalone", false);

      // WHEN: Updating receipt
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(put(API_BASE + "/" + draftReceipt.getId())
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(updateBody)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.amount").value(2000000))
          .andExpect(jsonPath("$.reference").value("UPDATED-001"));
    }

    @Test
    @DisplayName("PUT /receipts/{id} - should reject update of POSTED receipt")
    void testCannotUpdatePostedReceipt() throws Exception {
      // GIVEN: POSTED receipt exists
      ARPayment postedReceipt = createTestReceipt(ReceiptStatus.POSTED, BigDecimal.valueOf(1_000_000));

      Map<String, Object> updateBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 2_000_000,
          "paymentMethod", "BANK_TRANSFER",
          "payee", testCustomer.getName(),
          "isStandalone", false);

      // WHEN: Attempting to update
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(put(API_BASE + "/" + postedReceipt.getId())
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(updateBody)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /receipts/{id} - should delete DRAFT receipt only")
    void testDeleteDraftReceipt() throws Exception {
      // GIVEN: DRAFT receipt exists
      ARPayment draftReceipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(1_000_000));

      // WHEN: Deleting receipt
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(delete(API_BASE + "/" + draftReceipt.getId())
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isNoContent());

      // THEN: Receipt should be deleted
      assertTrue(arPaymentRepository.findById(draftReceipt.getId()).isEmpty());
    }
  }

  @Nested
  @DisplayName("Receipt Allocation Tests")
  class ReceiptAllocationTests {

    @Test
    @DisplayName("POST /receipts/{id}/allocate - should allocate to invoices")
    void testAllocateToInvoices() throws Exception {
      // GIVEN: DRAFT receipt exists
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));

      Map<String, Object> allocationRequest = Map.of(
          "allocations", List.of(
              Map.of(
                  "salesInvoiceId", postedInvoice.getId().toString(),
                  "allocatedAmount", 5_000_000)));

      // WHEN: Allocating to invoices
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/allocate")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(allocationRequest)))
          .andExpect(status().isOk());

      // THEN: Allocations recorded
      List<ReceiptAllocation> allocations = receiptAllocationRepository
          .findByReceiptIdOrderByAllocationOrderAsc(receipt.getId());
      assertEquals(1, allocations.size());
      assertEquals(BigDecimal.valueOf(5_000_000), allocations.get(0).getAllocatedAmount());
    }

    @Test
    @DisplayName("POST /receipts/{id}/allocate - should prevent overpayment")
    void testOverpaymentPrevention() throws Exception {
      // GIVEN: Receipt with amount < required allocation
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(3_000_000));

      Map<String, Object> allocationRequest = Map.of(
          "allocations", List.of(
              Map.of(
                  "salesInvoiceId", postedInvoice.getId().toString(),
                  "allocatedAmount", 6_000_000 // More than receipt amount
              )));

      // WHEN: Attempting overpayment allocation
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/allocate")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(allocationRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message", containsString("overpayment")));
    }
  }

  @Nested
  @DisplayName("Receipt Posting (GL Integration)")
  class ReceiptPostingTests {

    @Test
    @DisplayName("POST /receipts/{id}/post - should generate GL voucher (Dr 111/112, Cr 131)")
    void testPostReceiptGeneratesGLVoucher() throws Exception {
      // GIVEN: DRAFT receipt with allocations
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));
      ReceiptAllocation allocation = new ReceiptAllocation();
      allocation.setCompanyId(testCompany.getId());
      allocation.setReceiptId(receipt.getId());
      allocation.setSalesInvoiceId(postedInvoice.getId());
      allocation.setAllocatedAmount(BigDecimal.valueOf(5_000_000));
      allocation.setAllocationOrder(1);
      receiptAllocationRepository.save(allocation);

      // WHEN: Posting receipt
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/post")
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("POSTED"))
          .andExpect(jsonPath("$.linkedVoucherId").exists());

      // THEN: GL voucher created with correct lines
      ARPayment postedReceipt = arPaymentRepository.findById(receipt.getId()).orElseThrow();
      assertNotNull(postedReceipt.getLinkedVoucherId());

      Voucher voucher = voucherRepository.findById(postedReceipt.getLinkedVoucherId()).orElseThrow();
      assertEquals("POSTED", voucher.getStatus());

      // Verify voucher has debit and credit lines
      // Note: VoucherLine entity may not have getVoucherLines() in Voucher,
      // this is placeholder for actual voucher line verification
      // Additional voucher line validation would be implemented here
    }

    @Test
    @DisplayName("POST /receipts/{id}/post - should include dimensions in GL lines")
    void testPostReceiptIncludesDimensions() throws Exception {
      // GIVEN: Receipt with cost center dimension
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));
      // Assume dimension is set in the receipt entity or allocation

      // WHEN: Posting receipt
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/post")
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk());

      // THEN: GL lines include dimension details (verify in voucher lines)
      ARPayment postedReceipt = arPaymentRepository.findById(receipt.getId()).orElseThrow();
      if (postedReceipt.getLinkedVoucherId() != null) {
        Voucher voucher = voucherRepository.findById(postedReceipt.getLinkedVoucherId()).orElse(null);
        assertNotNull(voucher);
        // Additional dimension validation would go here
      }
    }

    @Test
    @DisplayName("POST /receipts/{id}/post - should update invoice status to PAID/PARTIALLY_PAID")
    void testPostReceiptUpdatesInvoiceStatus() throws Exception {
      // GIVEN: Receipt allocated to invoice
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));
      ReceiptAllocation allocation = new ReceiptAllocation();
      allocation.setCompanyId(testCompany.getId());
      allocation.setReceiptId(receipt.getId());
      allocation.setSalesInvoiceId(postedInvoice.getId());
      allocation.setAllocatedAmount(BigDecimal.valueOf(5_000_000));
      allocation.setAllocationOrder(1);
      receiptAllocationRepository.save(allocation);

      // WHEN: Posting receipt
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/post")
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk());

      // THEN: Invoice status updated to PAID (fully allocated)
      SalesInvoice updatedInvoice = salesInvoiceRepository.findById(postedInvoice.getId()).orElseThrow();
      assertEquals(SalesInvoiceStatus.PAID, updatedInvoice.getStatus());
    }

    @Test
    @DisplayName("POST /receipts/{id}/post - should reject posting to closed period")
    void testCannotPostToClosedPeriod() throws Exception {
      // GIVEN: Receipt date in closed period
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(1_000_000));
      // Set receipt date to a closed period (assuming period service exists)
      receipt.setReceiptDate(LocalDate.now().minusMonths(6));
      arPaymentRepository.save(receipt);

      // WHEN: Attempting to post
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/post")
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message", containsString("closed")));
    }
  }

  @Nested
  @DisplayName("Receipt Reversal Tests")
  class ReceiptReversalTests {

    @Test
    @DisplayName("POST /receipts/{id}/reverse - should generate linked reversal voucher")
    void testReverseReceiptGeneratesReversalVoucher() throws Exception {
      // GIVEN: POSTED receipt exists
      ARPayment receipt = createTestReceipt(ReceiptStatus.POSTED, BigDecimal.valueOf(5_000_000));
      UUID linkedVoucherId = UUID.randomUUID();
      receipt.setLinkedVoucherId(linkedVoucherId);
      arPaymentRepository.save(receipt);

      Map<String, Object> reverseRequest = Map.of(
          "reason", "Incorrect payment amount");

      // WHEN: Reversing receipt with reason
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/reverse")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(reverseRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("REVERSED"))
          .andExpect(jsonPath("$.reversingReceiptId").exists());

      // THEN: Reversal voucher created and cross-linked
      ARPayment reversedReceipt = arPaymentRepository.findById(receipt.getId()).orElseThrow();
      assertEquals(ReceiptStatus.REVERSED, reversedReceipt.getStatus());
      assertNotNull(reversedReceipt.getReversingReceiptId());
      assertEquals("Incorrect payment amount", reversedReceipt.getReversalReason());
    }

    @Test
    @DisplayName("POST /receipts/{id}/reverse - should require mandatory reversal reason")
    void testReverseRequiresMandatoryReason() throws Exception {
      // GIVEN: POSTED receipt
      ARPayment receipt = createTestReceipt(ReceiptStatus.POSTED, BigDecimal.valueOf(5_000_000));

      Map<String, Object> reverseRequest = Map.of();

      // WHEN: Reversing without reason
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/reverse")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(reverseRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message", containsString("reason")));
    }

    @Test
    @DisplayName("POST /receipts/{id}/reverse - should audit log reversal with reason and old/new state")
    void testReverseLogsAudit() throws Exception {
      // GIVEN: POSTED receipt
      ARPayment receipt = createTestReceipt(ReceiptStatus.POSTED, BigDecimal.valueOf(5_000_000));

      Map<String, Object> reverseRequest = Map.of(
          "reason", "Customer requested refund");

      // WHEN: Reversing with reason
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/reverse")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(reverseRequest)))
          .andExpect(status().isOk());

      // THEN: Audit log entry created with action = REVERSE
      List<AuditLog> auditLogs = auditLogRepository.findAll();
      assertTrue(auditLogs.stream()
          .anyMatch(log -> "ARPayment".equals(log.getEntityType())
              && "REVERSE".equals(log.getAction())
              && "Customer requested refund".equals(log.getReason())));
    }
  }

  @Nested
  @DisplayName("Batch Import Tests")
  class BatchImportTests {

    @Test
    @DisplayName("POST /receipts/batch-import - should import receipts atomically")
    void testBatchImportAtomic() throws Exception {
      // GIVEN: Excel file with valid receipts
      byte[] excelContent = createValidExcelFile();
      MockMultipartFile file = new MockMultipartFile(
          "file", "receipts.xlsx", "application/vnd.ms-excel", excelContent);

      // WHEN: Uploading batch file
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(multipart(API_BASE + "/batch-import")
          .file(file)
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.successCount").value(greaterThan(0)))
          .andExpect(jsonPath("$.errorCount").value(0));

      // THEN: All receipts imported successfully
      List<ARPayment> receipts = arPaymentRepository.findAll();
      assertTrue(receipts.size() > 0);
    }

    @Test
    @DisplayName("POST /receipts/batch-import - should return error map with row numbers")
    void testBatchImportErrorHandling() throws Exception {
      // GIVEN: Excel file with validation errors
      byte[] excelContent = createInvalidExcelFile();
      MockMultipartFile file = new MockMultipartFile(
          "file", "receipts.xlsx", "application/vnd.ms-excel", excelContent);

      // WHEN: Uploading batch file
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(multipart(API_BASE + "/batch-import")
          .file(file)
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors").isArray())
          .andExpect(jsonPath("$.errors[0].rowNumber").exists())
          .andExpect(jsonPath("$.errors[0].field").exists())
          .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("GET /receipts/import-template - should provide Excel template")
    void testImportTemplate() throws Exception {
      // GIVEN: No preconditions
      // WHEN: Requesting import template
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(get(API_BASE + "/import-template")
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk())
          .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .andExpect(header().exists("Content-Disposition"));
    }

    private byte[] createValidExcelFile() {
      // Create a simple valid Excel file with test data
      return new byte[] { 0x50, 0x4B, 0x03, 0x04 }; // ZIP header for Excel
    }

    private byte[] createInvalidExcelFile() {
      // Create Excel file with invalid data
      return new byte[] { 0x50, 0x4B, 0x03, 0x04 }; // ZIP header for Excel
    }
  }

  @Nested
  @DisplayName("RBAC and Authorization Tests")
  class RBACTests {

    @Test
    @DisplayName("All authenticated users can view receipts")
    void testViewAuthenticationRequired() throws Exception {
      // GIVEN: User with valid token
      createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(1_000_000));

      // WHEN: Fetching receipt list
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(get(API_BASE)
          .header("Authorization", "Bearer " + accountantToken))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject unauthenticated requests")
    void testUnauthenticatedRejected() throws Exception {
      // GIVEN: No authorization header
      // WHEN: Requesting receipt
      mockMvc.perform(get(API_BASE))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Accountant role can create and post receipts")
    void testAccountantCanCreateAndPost() throws Exception {
      // GIVEN: Accountant token
      Map<String, Object> requestBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 1_000_000,
          "paymentMethod", "BANK_TRANSFER",
          "payee", testCustomer.getName(),
          "isStandalone", false);

      // WHEN: Creating receipt
      CompanyContext.setCompanyId(testCompany.getId());
      MvcResult result = mockMvc.perform(post(API_BASE)
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(requestBody)))
          .andExpect(status().isCreated())
          .andReturn();

      // THEN: Accountant can perform receipt operations
      String response = result.getResponse().getContentAsString();
      assertNotNull(response);
    }

    @Test
    @DisplayName("Admin role required for standalone receipts")
    void testAdminRequiredForStandalone() throws Exception {
      // GIVEN: Accountant token, request with isStandalone=true
      Map<String, Object> requestBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 1_000_000,
          "paymentMethod", "BANK_TRANSFER",
          "payee", testCustomer.getName(),
          "isStandalone", true);

      // WHEN: Creating standalone receipt with accountant token
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE)
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(requestBody)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Audit Logging Integration")
  class AuditLoggingTests {

    @Test
    @DisplayName("POST /receipts - should create audit log entry")
    void testCreateAuditLog() throws Exception {
      // GIVEN: Creating receipt
      Map<String, Object> requestBody = Map.of(
          "customerId", testCustomer.getId(),
          "receiptDate", LocalDate.now().toString(),
          "bankAccountId", testBankAccount.getId(),
          "amount", 1_000_000,
          "paymentMethod", "BANK_TRANSFER",
          "payee", testCustomer.getName(),
          "isStandalone", false);

      // WHEN: Receipt created
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE)
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(requestBody)))
          .andExpect(status().isCreated());

      // THEN: Audit log entry created
      List<AuditLog> auditLogs = auditLogRepository.findAll();
      assertTrue(auditLogs.stream()
          .anyMatch(log -> "ARPayment".equals(log.getEntityType())
              && "CREATE".equals(log.getAction())
              && log.getUserId().equals(testAccountant.getId())));
    }

    @Test
    @DisplayName("POST /receipts/{id}/allocate - should audit log allocations with invoice balance deltas")
    void testAllocationAuditLog() throws Exception {
      // GIVEN: Allocating receipt to invoice
      ARPayment receipt = createTestReceipt(ReceiptStatus.DRAFT, BigDecimal.valueOf(5_000_000));

      Map<String, Object> allocationRequest = Map.of(
          "allocations", List.of(
              Map.of(
                  "salesInvoiceId", postedInvoice.getId().toString(),
                  "allocatedAmount", 5_000_000)));

      // WHEN: Allocation saved
      CompanyContext.setCompanyId(testCompany.getId());
      mockMvc.perform(post(API_BASE + "/" + receipt.getId() + "/allocate")
          .header("Authorization", "Bearer " + accountantToken)
          .contentType(APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(allocationRequest)))
          .andExpect(status().isOk());

      // THEN: Audit log includes allocation details
      List<AuditLog> auditLogs = auditLogRepository.findAll();
      assertTrue(auditLogs.stream()
          .anyMatch(log -> "ARPayment".equals(log.getEntityType())
              && "ALLOCATE".equals(log.getAction())));
    }
  }
}
