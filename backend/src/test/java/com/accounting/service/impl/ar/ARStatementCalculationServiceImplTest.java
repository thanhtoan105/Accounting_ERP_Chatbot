package com.accounting.service.impl.ar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.ARStatementDetailedDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import com.accounting.entity.*;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ARStatementCalculationServiceImplTest {

  @Mock private SalesInvoiceRepository invoiceRepository;
  @Mock private ReceiptAllocationRepository receiptAllocationRepository;
  @Mock private ARPaymentRepository paymentRepository;
  @Mock private CustomerRepository customerRepository;

  private ARStatementCalculationServiceImpl calculationService;
  private static final Long COMPANY_ID = 1L;
  private static final Long CUSTOMER_ID = 100L;

  @BeforeEach
  void setUp() {
    calculationService =
        new ARStatementCalculationServiceImpl(
            invoiceRepository,
            receiptAllocationRepository,
            paymentRepository,
            customerRepository);
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void testGenerateSummaryStatement_Success() {
    // Arrange
    LocalDate asOfDate = LocalDate.now();
    Customer customer = createMockCustomer();
    SalesInvoice invoice1 = createMockInvoice(UUID.randomUUID(), "INV-001", new BigDecimal("100000"), new BigDecimal("30000"));
    SalesInvoice invoice2 = createMockInvoice(UUID.randomUUID(), "INV-002", new BigDecimal("200000"), new BigDecimal("50000"));

    when(customerRepository.findByCompanyIdAndId(COMPANY_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer));
    when(invoiceRepository.findByCompanyIdAndInvoiceDateBetween(
            eq(COMPANY_ID), any(LocalDate.class), eq(asOfDate)))
        .thenReturn(Arrays.asList(invoice1, invoice2));

    // Act
    ARStatementSummaryDTO result = calculationService.generateSummaryStatement(CUSTOMER_ID, asOfDate);

    // Assert
    assertNotNull(result);
    assertEquals(CUSTOMER_ID, result.getCustomerId());
    assertEquals("Test Customer", result.getCustomerName());
    assertEquals(2, result.getInvoices().size());
    assertEquals(new BigDecimal("300000"), result.getTotalInvoices());
    assertEquals(new BigDecimal("80000"), result.getTotalPaid());
    assertEquals(new BigDecimal("220000"), result.getTotalOutstanding());
  }

  @Test
  void testGenerateSummaryStatement_CustomerNotFound() {
    // Arrange
    when(customerRepository.findByCompanyIdAndId(COMPANY_ID, CUSTOMER_ID))
        .thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(
        IllegalArgumentException.class,
        () -> calculationService.generateSummaryStatement(CUSTOMER_ID, LocalDate.now()));
  }

  @Test
  void testGenerateDetailedStatement_Success() {
    // Arrange
    LocalDate asOfDate = LocalDate.now();
    Customer customer = createMockCustomer();
    UUID invoiceId = UUID.randomUUID();
    SalesInvoice invoice = createMockInvoice(invoiceId, "INV-001", new BigDecimal("100000"), new BigDecimal("30000"));
    
    UUID receiptId = UUID.randomUUID();
    ARPayment payment = createMockPayment(receiptId, "REC-001", LocalDate.now());
    ReceiptAllocation allocation = createMockAllocation(receiptId, invoiceId, new BigDecimal("30000"));

    when(customerRepository.findByCompanyIdAndId(COMPANY_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer));
    when(invoiceRepository.findByCompanyIdAndInvoiceDateBetween(
            eq(COMPANY_ID), any(LocalDate.class), eq(asOfDate)))
        .thenReturn(Arrays.asList(invoice));
    when(receiptAllocationRepository.findBySalesInvoiceId(invoiceId))
        .thenReturn(Arrays.asList(allocation));
    when(paymentRepository.findById(receiptId))
        .thenReturn(Optional.of(payment));

    // Act
    ARStatementDetailedDTO result = calculationService.generateDetailedStatement(CUSTOMER_ID, asOfDate);

    // Assert
    assertNotNull(result);
    assertEquals(CUSTOMER_ID, result.getCustomerId());
    assertTrue(result.getTransactions().size() >= 2); // Invoice + Receipt
  }

  @Test
  void testGenerateSummaryStatement_ExcludesRejectedAndPaid() {
    // Arrange
    LocalDate asOfDate = LocalDate.now();
    Customer customer = createMockCustomer();
    SalesInvoice postedInvoice = createMockInvoice(UUID.randomUUID(), "INV-001", new BigDecimal("100000"), BigDecimal.ZERO);
    SalesInvoice rejectedInvoice = createMockInvoice(UUID.randomUUID(), "INV-002", new BigDecimal("50000"), BigDecimal.ZERO);
    rejectedInvoice.setStatus(SalesInvoiceStatus.REJECTED);
    SalesInvoice paidInvoice = createMockInvoice(UUID.randomUUID(), "INV-003", new BigDecimal("30000"), new BigDecimal("30000"));
    paidInvoice.setRemainingBalance(BigDecimal.ZERO);

    when(customerRepository.findByCompanyIdAndId(COMPANY_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer));
    when(invoiceRepository.findByCompanyIdAndInvoiceDateBetween(
            eq(COMPANY_ID), any(LocalDate.class), eq(asOfDate)))
        .thenReturn(Arrays.asList(postedInvoice, rejectedInvoice, paidInvoice));

    // Act
    ARStatementSummaryDTO result = calculationService.generateSummaryStatement(CUSTOMER_ID, asOfDate);

    // Assert
    assertEquals(1, result.getInvoices().size()); // Only posted invoice included
    assertEquals("INV-001", result.getInvoices().get(0).getInvoiceNumber());
  }

  private Customer createMockCustomer() {
    Customer customer = new Customer();
    customer.setId(CUSTOMER_ID);
    customer.setCompanyId(COMPANY_ID);
    customer.setName("Test Customer");
    customer.setCode("CUST-001");
    customer.setAddress("123 Test St");
    customer.setTaxCode("123456789");
    return customer;
  }

  private SalesInvoice createMockInvoice(UUID id, String invoiceNumber, BigDecimal totalAmount, BigDecimal amountPaid) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(id);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setCustomerId(CUSTOMER_ID);
    invoice.setInvoiceNumber(invoiceNumber);
    invoice.setInvoiceDate(LocalDate.now().minusDays(10));
    invoice.setTotalAmount(totalAmount);
    invoice.setAmountPaid(amountPaid);
    invoice.setRemainingBalance(totalAmount.subtract(amountPaid));
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    return invoice;
  }

  private ARPayment createMockPayment(UUID id, String receiptNumber, LocalDate receiptDate) {
    ARPayment payment = new ARPayment();
    payment.setId(id);
    payment.setCompanyId(COMPANY_ID);
    payment.setCustomerId(CUSTOMER_ID);
    payment.setReceiptNumber(receiptNumber);
    payment.setReceiptDate(receiptDate);
    payment.setAmount(new BigDecimal("30000"));
    payment.setStatus(ReceiptStatus.POSTED);
    return payment;
  }

  private ReceiptAllocation createMockAllocation(UUID receiptId, UUID invoiceId, BigDecimal amount) {
    ReceiptAllocation allocation = new ReceiptAllocation();
    allocation.setId(UUID.randomUUID());
    allocation.setCompanyId(COMPANY_ID);
    allocation.setReceiptId(receiptId);
    allocation.setSalesInvoiceId(invoiceId);
    allocation.setAllocatedAmount(amount);
    allocation.setAllocationOrder(1);
    return allocation;
  }
}

