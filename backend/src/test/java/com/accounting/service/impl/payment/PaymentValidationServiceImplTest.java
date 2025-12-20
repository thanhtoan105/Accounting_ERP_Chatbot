package com.accounting.service.impl.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.dto.PaymentValidationResult;
import com.accounting.entity.APPayment;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.PaymentValidationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentValidationServiceImplTest {

  @Mock private SupplierRepository supplierRepository;

  @Mock private PurchaseBillRepository purchaseBillRepository;

  @Mock private PaymentAllocationRepository paymentAllocationRepository;

  @Mock private AccountBalanceService accountBalanceService;

  @Mock private CompanySettingsService companySettingsService;

  @Mock private SecurityContext securityContext;

  @Mock private Authentication authentication;

  private PaymentValidationService validationService;

  @BeforeEach
  void setUp() {
    validationService =
        new PaymentValidationServiceImpl(
            purchaseBillRepository,
            paymentAllocationRepository,
            supplierRepository,
            accountBalanceService,
            companySettingsService);
    CompanyContext.setCompanyId(1L);

    // Mock security context
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getAuthorities())
        .thenAnswer(invocation -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_accountant")));
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void validateSupplierHasOpenBills_withOpenBills_returnsValid() {
    // Setup
    Long supplierId = 1L;
    PurchaseBill openBill = createPurchaseBill(UUID.randomUUID(), PurchaseBillStatus.POSTED, new BigDecimal("1000"));
    when(purchaseBillRepository.findByCompanyIdAndStatus(1L, PurchaseBillStatus.POSTED))
        .thenReturn(Arrays.asList(openBill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(any(UUID.class)))
        .thenReturn(BigDecimal.ZERO);

    // Execute
    PaymentValidationResult result = validationService.validateSupplierHasOpenBills(supplierId);

    // Assert
    assertTrue(result.isValid());
  }

  @Test
  void validateSupplierHasOpenBills_noOpenBills_returnsInvalid() {
    // Setup
    Long supplierId = 1L;
    when(purchaseBillRepository.findByCompanyIdAndStatus(1L, PurchaseBillStatus.POSTED))
        .thenReturn(Collections.emptyList());

    // Execute
    PaymentValidationResult result = validationService.validateSupplierHasOpenBills(supplierId);

    // Assert
    assertFalse(result.isValid());
    assertTrue(result.getFieldErrors().containsKey("supplierId"));
  }

  @Test
  void validateAllocations_validAllocations_returnsValid() {
    // Setup
    BigDecimal paymentAmount = new BigDecimal("1000");
    UUID billId1 = UUID.randomUUID();
    UUID billId2 = UUID.randomUUID();
    PurchaseBill bill1 = createPurchaseBill(billId1, PurchaseBillStatus.POSTED, new BigDecimal("2000"));
    PurchaseBill bill2 = createPurchaseBill(billId2, PurchaseBillStatus.POSTED, new BigDecimal("1500"));

    when(purchaseBillRepository.findByCompanyIdAndId(1L, billId1)).thenReturn(Optional.of(bill1));
    when(purchaseBillRepository.findByCompanyIdAndId(1L, billId2)).thenReturn(Optional.of(bill2));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(billId1)).thenReturn(BigDecimal.ZERO);
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(billId2)).thenReturn(BigDecimal.ZERO);

    List<PaymentAllocationRequest> allocations =
        Arrays.asList(
            new PaymentAllocationRequest(billId1, new BigDecimal("600")),
            new PaymentAllocationRequest(billId2, new BigDecimal("400")));

    // Execute
    PaymentValidationResult result =
        validationService.validateAllocations(allocations, paymentAmount);

    // Assert
    assertTrue(result.isValid());
  }

  @Test
  void validateAllocations_overAllocated_returnsInvalid() {
    // Setup
    BigDecimal paymentAmount = new BigDecimal("1000");
    UUID billId1 = UUID.randomUUID();
    PurchaseBill bill1 = createPurchaseBill(billId1, PurchaseBillStatus.POSTED, new BigDecimal("500"));

    when(purchaseBillRepository.findByCompanyIdAndId(1L, billId1)).thenReturn(Optional.of(bill1));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(billId1)).thenReturn(BigDecimal.ZERO);

    List<PaymentAllocationRequest> allocations =
        Arrays.asList(new PaymentAllocationRequest(billId1, new BigDecimal("600"))); // Over-allocated

    // Execute
    PaymentValidationResult result =
        validationService.validateAllocations(allocations, paymentAmount);

    // Assert
    assertFalse(result.isValid());
  }

  @Test
  void validateAccountBalance_sufficientBalance_returnsValid() {
    // Setup
    Long accountId = 1L;
    BigDecimal paymentAmount = new BigDecimal("1000");
    when(accountBalanceService.validateSufficientBalance(accountId, paymentAmount)).thenReturn(true);

    // Execute
    PaymentValidationResult result =
        validationService.validateAccountBalance(accountId, paymentAmount);

    // Assert
    assertTrue(result.isValid());
  }

  @Test
  void validateAccountBalance_insufficientBalance_returnsInvalid() {
    // Setup
    Long accountId = 1L;
    BigDecimal paymentAmount = new BigDecimal("1000");
    when(accountBalanceService.validateSufficientBalance(accountId, paymentAmount)).thenReturn(false);

    // Execute
    PaymentValidationResult result =
        validationService.validateAccountBalance(accountId, paymentAmount);

    // Assert
    assertFalse(result.isValid());
  }

  @Test
  void validateStandalonePayment_adminUser_returnsValid() {
    // Setup
    when(authentication.getAuthorities())
        .thenAnswer(invocation -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

    APPayment payment = createPayment(true);

    // Execute
    PaymentValidationResult result =
        validationService.validateStandalonePayment(payment, 1L);

    // Assert
    assertTrue(result.isValid());
  }

  @Test
  void validateStandalonePayment_nonAdminUser_returnsInvalid() {
    // Setup
    when(authentication.getAuthorities())
        .thenAnswer(invocation -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_accountant")));

    APPayment payment = createPayment(true);

    // Execute
    PaymentValidationResult result =
        validationService.validateStandalonePayment(payment, 1L);

    // Assert
    assertFalse(result.isValid());
  }

  @Test
  void validatePaymentProof_highAmountWithoutProof_returnsInvalid() {
    // Setup
    BigDecimal highAmount = new BigDecimal("10000000"); // 10M VND

    // Execute
    PaymentValidationResult result =
        validationService.validatePaymentProof(highAmount, null);

    // Assert
    assertFalse(result.isValid());
  }

  @Test
  void validatePaymentProof_highAmountWithProof_returnsValid() {
    // Setup
    BigDecimal highAmount = new BigDecimal("10000000"); // 10M VND
    String proofUrl = "https://example.com/proof.pdf";

    // Execute
    PaymentValidationResult result =
        validationService.validatePaymentProof(highAmount, proofUrl);

    // Assert
    assertTrue(result.isValid());
  }

  // Helper methods
  private PurchaseBill createPurchaseBill(UUID id, PurchaseBillStatus status, BigDecimal totalAmount) {
    PurchaseBill bill = new PurchaseBill();
    bill.setId(id);
    bill.setCompanyId(1L);
    bill.setSupplierId(1L);
    bill.setBillNumber("BILL-" + id.toString().substring(0, 8));
    bill.setBillDate(LocalDate.now());
    bill.setDueDate(LocalDate.now().plusDays(30));
    bill.setStatus(status);
    bill.setTotalAmount(totalAmount);
    bill.setCreatedAt(Instant.now());
    bill.setUpdatedAt(Instant.now());
    return bill;
  }

  private APPayment createPayment(boolean isStandalone) {
    APPayment payment = new APPayment();
    payment.setId(UUID.randomUUID());
    payment.setCompanyId(1L);
    payment.setSupplierId(1L);
    payment.setPaymentNumber("PAY-001");
    payment.setPaymentDate(LocalDate.now());
    payment.setAmount(new BigDecimal("1000"));
    payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
    payment.setIsStandalone(isStandalone);
    payment.setStatus(PaymentStatus.DRAFT);
    payment.setCreatedAt(Instant.now());
    payment.setUpdatedAt(Instant.now());
    return payment;
  }
}
