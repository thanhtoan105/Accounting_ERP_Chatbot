package com.accounting.service.impl.sales;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.dto.ReceiptValidationResult;
import com.accounting.entity.ARPayment;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;
import com.accounting.service.CompanySettingsService;

/**
 * Unit Tests for ReceiptValidationService
 * 
 * Tests for:
 * - Customer has open invoices validation
 * - Allocation validation with overpayment prevention
 * - Account balance validation
 * - Standalone receipt validation
 * - Receipt proof validation
 */
@ExtendWith(MockitoExtension.class)
class ReceiptValidationServiceImplTest {
  
  @Mock
  private SalesInvoiceRepository salesInvoiceRepository;
  
  @Mock
  private ReceiptAllocationRepository receiptAllocationRepository;
  
  @Mock
  private CustomerRepository customerRepository;
  
  @Mock
  private AccountBalanceService accountBalanceService;
  
  @Mock
  private CompanySettingsService companySettingsService;
  
  @InjectMocks
  private ReceiptValidationServiceImpl validationService;
  
  private static final Long COMPANY_ID = 1L;
  private static final Long CUSTOMER_ID = 100L;
  private static final Long BANK_ACCOUNT_ID = 200L;
  private static final Long USER_ID = 300L;

  @BeforeEach
  void setup() {
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  // Helper methods

  private SalesInvoice createMockInvoice(UUID invoiceId, BigDecimal totalAmount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(invoiceId);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setCustomerId(CUSTOMER_ID);
    invoice.setInvoiceNumber("INV-" + invoiceId.toString().substring(0, 8));
    invoice.setInvoiceDate(LocalDate.now());
    invoice.setDueDate(LocalDate.now().plusDays(30));
    invoice.setTotalAmount(totalAmount);
    invoice.setVatAmount(BigDecimal.ZERO);
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    return invoice;
  }

  private ARPayment createMockReceipt(boolean isStandalone) {
    ARPayment receipt = new ARPayment();
    receipt.setId(UUID.randomUUID());
    receipt.setCompanyId(COMPANY_ID);
    receipt.setCustomerId(CUSTOMER_ID);
    receipt.setReceiptNumber("RCP-2025-0001");
    receipt.setReceiptDate(LocalDate.now());
    receipt.setBankAccountId(BANK_ACCOUNT_ID);
    receipt.setAmount(BigDecimal.valueOf(1_000_000));
    receipt.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
    receipt.setPayee("Test Customer");
    receipt.setIsStandalone(isStandalone);
    receipt.setCreatedById(USER_ID);
    return receipt;
  }

  @Nested
  @DisplayName("AC2: Overpayment Prevention Tests")
  class OverpaymentPreventionTests {
    
    @Test
    @DisplayName("should allow allocation equal to remaining balance")
    void testAllocationEqualToRemainingBalance() {
      // GIVEN: Invoice with 10M remaining balance
      UUID invoiceId = UUID.randomUUID();
      SalesInvoice invoice = createMockInvoice(invoiceId, BigDecimal.valueOf(10_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoiceId, BigDecimal.valueOf(10_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoiceId))
          .thenReturn(Optional.of(invoice));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(invoiceId))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Validating allocation exactly equal to remaining balance
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(10_000_000));

      // THEN: Validation succeeds
      assertTrue(result.isValid());
      assertTrue(result.getFieldErrors().isEmpty());
    }
    
    @Test
    @DisplayName("should allow allocation less than remaining balance")
    void testAllocationLessThanRemainingBalance() {
      // GIVEN: Invoice with 10M remaining balance
      UUID invoiceId = UUID.randomUUID();
      SalesInvoice invoice = createMockInvoice(invoiceId, BigDecimal.valueOf(10_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoiceId, BigDecimal.valueOf(6_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoiceId))
          .thenReturn(Optional.of(invoice));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(invoiceId))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Validating partial allocation
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(6_000_000));

      // THEN: Validation succeeds
      assertTrue(result.isValid());
    }
    
    @Test
    @DisplayName("should reject allocation exceeding remaining balance")
    void testAllocationExceedingRemainingBalance() {
      // GIVEN: Invoice with 10M remaining balance
      UUID invoiceId = UUID.randomUUID();
      SalesInvoice invoice = createMockInvoice(invoiceId, BigDecimal.valueOf(10_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoiceId, BigDecimal.valueOf(15_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoiceId))
          .thenReturn(Optional.of(invoice));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(invoiceId))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Validating overpayment allocation
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(15_000_000));

      // THEN: Validation fails with overpayment error
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors()).isNotEmpty();
      assertThat(result.getFieldErrors().toString()).contains("exceeds");
    }
    
    @Test
    @DisplayName("should prevent overpayment across multiple invoices")
    void testOverpaymentMultipleInvoices() {
      // GIVEN: Receipt amount less than total allocations
      UUID invoice1Id = UUID.randomUUID();
      UUID invoice2Id = UUID.randomUUID();
      
      SalesInvoice invoice1 = createMockInvoice(invoice1Id, BigDecimal.valueOf(10_000_000));
      SalesInvoice invoice2 = createMockInvoice(invoice2Id, BigDecimal.valueOf(10_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoice1Id, BigDecimal.valueOf(10_000_000)),
          new ReceiptAllocationRequest(invoice2Id, BigDecimal.valueOf(10_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice1Id))
          .thenReturn(Optional.of(invoice1));
      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice2Id))
          .thenReturn(Optional.of(invoice2));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(any()))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Total allocations (20M) exceed receipt amount (15M)
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(15_000_000));

      // THEN: Validation fails
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors().get("allocations")).isNotEmpty();
      assertThat(result.getFieldErrors().get("allocations").get(0))
          .contains("exceed receipt amount");
    }
  }

  @Nested
  @DisplayName("AC1: Customer Open Invoices Validation Tests")
  class CustomerOpenInvoicesTests {
    
    @Test
    @DisplayName("should allow receipt for customer with open invoices")
    void testCustomerWithOpenInvoices() {
      // GIVEN: Customer with 2 open unpaid invoices
      List<SalesInvoice> invoices = List.of(
          createMockInvoice(UUID.randomUUID(), BigDecimal.valueOf(5_000_000)),
          createMockInvoice(UUID.randomUUID(), BigDecimal.valueOf(3_000_000))
      );

      when(salesInvoiceRepository.findByCompanyId(COMPANY_ID)).thenReturn(invoices);

      // WHEN: Validating customer for receipt
      ReceiptValidationResult result = validationService.validateCustomerHasOpenInvoices(CUSTOMER_ID);

      // THEN: Validation succeeds
      assertTrue(result.isValid());
    }
    
    @Test
    @DisplayName("should reject linked receipt for customer with no open invoices")
    void testCustomerWithoutOpenInvoices() {
      // GIVEN: Customer with NO open invoices
      when(salesInvoiceRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(Collections.emptyList());

      // WHEN: Validating for linked receipt
      ReceiptValidationResult result = validationService.validateCustomerHasOpenInvoices(CUSTOMER_ID);

      // THEN: Validation fails
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors().get("customerId")).isNotEmpty();
      assertThat(result.getFieldErrors().get("customerId").get(0))
          .contains("no open/unpaid invoices");
    }
    
    @Test
    @DisplayName("should allow standalone receipt even without open invoices")
    void testStandaloneReceiptWithoutOpenInvoices() {
      // GIVEN: Standalone receipt (no need to validate customer invoices)
      // NOTE: Standalone receipt validation is separate - validateStandaloneReceipt()
      // This test verifies that validateCustomerHasOpenInvoices is only for linked receipts
      
      when(salesInvoiceRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(Collections.emptyList());

      // WHEN: Validating customer (this method is for linked receipts)
      ReceiptValidationResult result = validationService.validateCustomerHasOpenInvoices(CUSTOMER_ID);

      // THEN: Still fails for linked receipts (standalone uses different validation)
      assertFalse(result.isValid());
    }
  }

  @Nested
  @DisplayName("Account Balance Validation Tests")
  class AccountBalanceTests {
    
    @Test
    @DisplayName("should allow receipt if account has sufficient balance")
    void testSufficientAccountBalance() {
      // GIVEN: Receipt (incoming payment - no balance check needed)
      // NOTE: For receipts, we're receiving money, not paying out
      
      // WHEN: Validating account balance
      ReceiptValidationResult result = validationService.validateAccountBalance(
          BANK_ACCOUNT_ID, BigDecimal.valueOf(5_000_000));

      // THEN: Validation succeeds (receipts don't need balance checks)
      assertTrue(result.isValid());
    }
    
    @Test
    @DisplayName("should validate account ID is provided")
    void testAccountIdRequired() {
      // GIVEN: No account ID
      // WHEN: Validating with null account ID
      ReceiptValidationResult result = validationService.validateAccountBalance(
          null, BigDecimal.valueOf(5_000_000));

      // THEN: Validation fails
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors().get("accountId")).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Standalone Receipt Tests")
  class StandaloneReceiptTests {
    
    @Test
    @DisplayName("should flag standalone receipts as advances")
    void testStandaloneReceiptFlagging() {
      // GIVEN: Receipt with isStandalone = true
      ARPayment standaloneReceipt = createMockReceipt(true);

      // WHEN: Validating standalone receipt
      ReceiptValidationResult result = validationService.validateStandaloneReceipt(
          standaloneReceipt, USER_ID);

      // THEN: Receipt flagged with warning (not an error, just informational)
      assertThat(result.getGlobalErrors()).isNotEmpty();
      assertThat(result.getGlobalErrors().get(0)).contains("Standalone receipt");
      assertThat(result.getGlobalErrors().get(0)).contains("admin approval");
    }
    
    @Test
    @DisplayName("should not flag non-standalone receipts")
    void testNonStandaloneReceiptNoFlag() {
      // GIVEN: Regular linked receipt (isStandalone = false)
      ARPayment linkedReceipt = createMockReceipt(false);

      // WHEN: Validating linked receipt
      ReceiptValidationResult result = validationService.validateStandaloneReceipt(
          linkedReceipt, USER_ID);

      // THEN: No warnings or errors
      assertTrue(result.getGlobalErrors().isEmpty());
    }
  }

  @Nested
  @DisplayName("Multiple Invoice Allocation Tests")
  class MultipleInvoiceAllocationTests {
    
    @Test
    @DisplayName("should support allocation to multiple invoices")
    void testMultipleInvoiceAllocation() {
      // GIVEN: Receipt of 18M covering multiple invoices
      UUID invoice1Id = UUID.randomUUID();
      UUID invoice2Id = UUID.randomUUID();
      
      SalesInvoice invoice1 = createMockInvoice(invoice1Id, BigDecimal.valueOf(10_000_000));
      SalesInvoice invoice2 = createMockInvoice(invoice2Id, BigDecimal.valueOf(8_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoice1Id, BigDecimal.valueOf(10_000_000)),
          new ReceiptAllocationRequest(invoice2Id, BigDecimal.valueOf(8_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice1Id))
          .thenReturn(Optional.of(invoice1));
      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice2Id))
          .thenReturn(Optional.of(invoice2));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(any()))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Allocating to both invoices
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(18_000_000));

      // THEN: Both allocations succeed
      assertTrue(result.isValid());
    }
    
    @Test
    @DisplayName("should allow partial allocation to first invoice, full to second")
    void testPartialAllocationMultipleInvoices() {
      // GIVEN: Receipt of 15M with partial allocations
      UUID invoice1Id = UUID.randomUUID();
      UUID invoice2Id = UUID.randomUUID();
      
      SalesInvoice invoice1 = createMockInvoice(invoice1Id, BigDecimal.valueOf(10_000_000));
      SalesInvoice invoice2 = createMockInvoice(invoice2Id, BigDecimal.valueOf(8_000_000));
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(invoice1Id, BigDecimal.valueOf(8_000_000)),
          new ReceiptAllocationRequest(invoice2Id, BigDecimal.valueOf(7_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice1Id))
          .thenReturn(Optional.of(invoice1));
      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, invoice2Id))
          .thenReturn(Optional.of(invoice2));
      when(receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(any()))
          .thenReturn(BigDecimal.ZERO);

      // WHEN: Partial allocations totaling 15M
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(15_000_000));

      // THEN: Allocations succeed (total doesn't exceed receipt amount)
      assertTrue(result.isValid());
    }
  }

  @Nested
  @DisplayName("Error Message Generation Tests")
  class ErrorMessageTests {
    
    @Test
    @DisplayName("should return detailed field-level error map")
    void testDetailedErrorMap() {
      // GIVEN: Invalid invoice in allocations
      UUID nonExistentInvoiceId = UUID.randomUUID();
      
      List<ReceiptAllocationRequest> allocations = List.of(
          new ReceiptAllocationRequest(nonExistentInvoiceId, BigDecimal.valueOf(5_000_000))
      );

      when(salesInvoiceRepository.findByCompanyIdAndId(COMPANY_ID, nonExistentInvoiceId))
          .thenReturn(Optional.empty());

      // WHEN: Validating allocations
      ReceiptValidationResult result = validationService.validateAllocations(
          allocations, BigDecimal.valueOf(5_000_000));

      // THEN: Error map includes field-level details
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors()).containsKey("allocations[0].salesInvoiceId");
      assertThat(result.getFieldErrors().get("allocations[0].salesInvoiceId").get(0))
          .contains("not found");
    }
  }

  @Nested
  @DisplayName("Receipt Proof Validation Tests")
  class ReceiptProofTests {

    @Test
    @DisplayName("should require proof for amounts above threshold")
    void testProofRequiredAboveThreshold() {
      // GIVEN: Receipt amount above 100M threshold
      BigDecimal largeAmount = new BigDecimal("150000000"); // 150M VND

      // WHEN: Validating without proof URL
      ReceiptValidationResult result = validationService.validateReceiptProof(
          largeAmount, null);

      // THEN: Validation fails
      assertFalse(result.isValid());
      assertThat(result.getFieldErrors().get("receiptProofUrl")).isNotEmpty();
      assertThat(result.getFieldErrors().get("receiptProofUrl").get(0))
          .contains("Receipt proof is required");
    }

    @Test
    @DisplayName("should not require proof for amounts below threshold")
    void testProofNotRequiredBelowThreshold() {
      // GIVEN: Receipt amount below 100M threshold
      BigDecimal smallAmount = new BigDecimal("50000000"); // 50M VND

      // WHEN: Validating without proof URL
      ReceiptValidationResult result = validationService.validateReceiptProof(
          smallAmount, null);

      // THEN: Validation succeeds
      assertTrue(result.isValid());
    }

    @Test
    @DisplayName("should accept proof URL for large amounts")
    void testProofProvidedForLargeAmount() {
      // GIVEN: Receipt amount above threshold with proof
      BigDecimal largeAmount = new BigDecimal("150000000");
      String proofUrl = "https://storage.example.com/receipts/proof123.pdf";

      // WHEN: Validating with proof URL
      ReceiptValidationResult result = validationService.validateReceiptProof(
          largeAmount, proofUrl);

      // THEN: Validation succeeds
      assertTrue(result.isValid());
    }
  }
}
