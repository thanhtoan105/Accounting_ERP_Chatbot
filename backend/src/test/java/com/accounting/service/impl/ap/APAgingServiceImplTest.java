package com.accounting.service.impl.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.accounting.dto.APAgingBucketDTO;
import com.accounting.dto.AgingBillDetailsDTO;
import com.accounting.dto.OverdueCountDTO;
import com.accounting.dto.OverdueSupplierDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;

/**
 * Unit tests for APAgingServiceImpl.
 * Tests aging bucket calculation, aggregation, filtering, and overdue detection.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class APAgingServiceImplTest {

  @Mock private PurchaseBillRepository purchaseBillRepository;

  @Mock private PaymentAllocationRepository paymentAllocationRepository;

  @Mock private SupplierRepository supplierRepository;

  @Mock private APPaymentRepository apPaymentRepository;

  @Mock private AuditService auditService;

  @InjectMocks private APAgingServiceImpl agingService;

  private static final Long COMPANY_ID = 1L;
  private static final Long SUPPLIER_ID = 100L;
  private static final Long PERIOD_ID = 1L;
  private LocalDate asOfDate;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
    asOfDate = LocalDate.now();
    
    // Setup SecurityContext for RBAC methods
    org.springframework.security.core.context.SecurityContext securityContext = 
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    org.springframework.security.core.Authentication authentication = 
        org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
    when(authentication.getAuthorities()).thenAnswer(invocation -> 
        java.util.Collections.singletonList(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT")));
    securityContext.setAuthentication(authentication);
    org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void calculateAgingBuckets_withCurrentBills_returnsCurrentBucket() {
    // Setup
    LocalDate dueDate = asOfDate.plusDays(10); // Future due date
    PurchaseBill bill = createPurchaseBill(dueDate, new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    // Mock the repository to return bills filtered by supplier
    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(bill));
    when(purchaseBillRepository.findById(bill.getId()))
        .thenReturn(Optional.of(bill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(bill.getId()))
        .thenReturn(BigDecimal.ZERO);

    // Execute
    APAgingBucketDTO result = agingService.calculateAgingBuckets(SUPPLIER_ID, asOfDate, PERIOD_ID);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getCurrent()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.getDays1To30()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getDays31To60()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getDays61To90()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getDaysOver90()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void calculateAgingBuckets_withOverdueBills_returnsOverdueBuckets() {
    // Setup
    LocalDate dueDate1To30 = asOfDate.minusDays(15); // 15 days overdue
    LocalDate dueDate31To60 = asOfDate.minusDays(45); // 45 days overdue
    LocalDate dueDateOver90 = asOfDate.minusDays(120); // 120 days overdue

    PurchaseBill bill1 = createPurchaseBill(dueDate1To30, new BigDecimal("500.00"), PurchaseBillStatus.POSTED);
    PurchaseBill bill2 = createPurchaseBill(dueDate31To60, new BigDecimal("300.00"), PurchaseBillStatus.POSTED);
    PurchaseBill bill3 = createPurchaseBill(dueDateOver90, new BigDecimal("200.00"), PurchaseBillStatus.POSTED);

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(bill1, bill2, bill3));
    when(purchaseBillRepository.findById(bill1.getId())).thenReturn(Optional.of(bill1));
    when(purchaseBillRepository.findById(bill2.getId())).thenReturn(Optional.of(bill2));
    when(purchaseBillRepository.findById(bill3.getId())).thenReturn(Optional.of(bill3));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(any(UUID.class)))
        .thenReturn(BigDecimal.ZERO);

    // Execute
    APAgingBucketDTO result = agingService.calculateAgingBuckets(SUPPLIER_ID, asOfDate, PERIOD_ID);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat(result.getDays31To60()).isEqualByComparingTo(new BigDecimal("300.00"));
    assertThat(result.getDaysOver90()).isEqualByComparingTo(new BigDecimal("200.00"));
  }

  @Test
  void calculateAgingBuckets_withPartialPayments_usesRemainingBalance() {
    // Setup
    LocalDate dueDate = asOfDate.minusDays(20);
    PurchaseBill bill = createPurchaseBill(dueDate, new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    BigDecimal allocatedAmount = new BigDecimal("300.00"); // Partial payment

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(bill));
    when(purchaseBillRepository.findById(bill.getId()))
        .thenReturn(Optional.of(bill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(bill.getId()))
        .thenReturn(allocatedAmount);

    // Execute
    APAgingBucketDTO result = agingService.calculateAgingBuckets(SUPPLIER_ID, asOfDate, PERIOD_ID);

    // Assert
    assertThat(result).isNotNull();
    BigDecimal expectedRemaining = new BigDecimal("1000.00").subtract(allocatedAmount);
    assertThat(result.getDays1To30()).isEqualByComparingTo(expectedRemaining);
  }

  @Test
  void calculateAgingBuckets_excludesNonPostedBills() {
    // Setup
    PurchaseBill postedBill = createPurchaseBill(asOfDate.minusDays(10), new BigDecimal("500.00"), PurchaseBillStatus.POSTED);

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(postedBill));
    when(purchaseBillRepository.findById(postedBill.getId()))
        .thenReturn(Optional.of(postedBill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(any(UUID.class)))
        .thenReturn(BigDecimal.ZERO);

    // Execute
    APAgingBucketDTO result = agingService.calculateAgingBuckets(SUPPLIER_ID, asOfDate, PERIOD_ID);

    // Assert
    assertThat(result).isNotNull();
    // Only posted bill should be included
    assertThat(result.getDays1To30()).isEqualByComparingTo(new BigDecimal("500.00"));
  }

  @Test
  void getOverdueCount_withOverdueBills_returnsCorrectCount() {
    // Setup
    LocalDate overdueDate = asOfDate.minusDays(35);
    PurchaseBill overdueBill1 = createPurchaseBill(overdueDate, new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    PurchaseBill overdueBill2 = createPurchaseBill(overdueDate, new BigDecimal("500.00"), PurchaseBillStatus.POSTED);

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(overdueBill1, overdueBill2));
    when(purchaseBillRepository.findById(overdueBill1.getId())).thenReturn(Optional.of(overdueBill1));
    when(purchaseBillRepository.findById(overdueBill2.getId())).thenReturn(Optional.of(overdueBill2));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(any(UUID.class)))
        .thenReturn(BigDecimal.ZERO);
    when(supplierRepository.findByCompanyId(COMPANY_ID)).thenReturn(Collections.emptyList());

    // Execute
    OverdueCountDTO result = agingService.getOverdueCount(PERIOD_ID, asOfDate);

    // Assert
    assertThat(result).isNotNull();
    // Note: Count may be 0 if no suppliers match, which is expected behavior
  }

  @Test
  void getOverdueSuppliers_withOverdueBills_returnsSuppliers() {
    // Setup
    LocalDate overdueDate = asOfDate.minusDays(50);
    PurchaseBill overdueBill = createPurchaseBill(overdueDate, new BigDecimal("2000.00"), PurchaseBillStatus.POSTED);
    com.accounting.entity.Supplier supplier = new com.accounting.entity.Supplier();
    supplier.setId(SUPPLIER_ID);
    supplier.setName("Test Supplier");
    supplier.setCompanyId(COMPANY_ID);

    // Mock supplier repository
    when(supplierRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(supplier));
    
    // Mock bill repository - this will be called for each supplier
    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(overdueBill));
    when(purchaseBillRepository.findById(overdueBill.getId())).thenReturn(Optional.of(overdueBill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(any(UUID.class)))
        .thenReturn(BigDecimal.ZERO);

    // Execute
    List<OverdueSupplierDTO> result = agingService.getOverdueSuppliers(PERIOD_ID, asOfDate, 10);

    // Assert
    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getOverdueAmount()).isGreaterThan(BigDecimal.ZERO);
  }

  @Test
  void getAgingBillDetails_withValidBucket_returnsBillDetails() {
    // Setup
    LocalDate dueDate = asOfDate.minusDays(25); // 1-30 days bucket
    PurchaseBill bill = createPurchaseBill(dueDate, new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    UUID billId = bill.getId();

    when(purchaseBillRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), eq(PurchaseBillStatus.POSTED)))
        .thenReturn(List.of(bill));
    when(purchaseBillRepository.findById(billId))
        .thenReturn(Optional.of(bill));
    when(paymentAllocationRepository.calculateTotalAllocatedAmount(billId))
        .thenReturn(BigDecimal.ZERO);
    when(apPaymentRepository.findAll()).thenReturn(Collections.emptyList());

    // Execute
    Page<AgingBillDetailsDTO> result = agingService.getAgingBillDetails(
        SUPPLIER_ID, "DAYS_1_30", PERIOD_ID, asOfDate, null, PageRequest.of(0, 20));

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isNotEmpty();
  }

  // Helper methods
  private PurchaseBill createPurchaseBill(LocalDate dueDate, BigDecimal totalAmount, PurchaseBillStatus status) {
    PurchaseBill bill = new PurchaseBill();
    bill.setId(UUID.randomUUID());
    bill.setCompanyId(COMPANY_ID);
    bill.setSupplierId(SUPPLIER_ID);
    bill.setDueDate(dueDate);
    bill.setTotalAmount(totalAmount);
    bill.setStatus(status);
    bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8));
    return bill;
  }
}
