package com.accounting.service.impl.ar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.ARReconciliationImportDTO;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.ARStatementDisputeRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ARReconciliationImportServiceImplTest {

  @Mock private SalesInvoiceRepository invoiceRepository;
  @Mock private ARStatementDisputeRepository disputeRepository;

  private ARReconciliationImportServiceImpl importService;
  private static final Long COMPANY_ID = 1L;
  private static final Long CUSTOMER_ID = 100L;

  @BeforeEach
  void setUp() {
    importService = new ARReconciliationImportServiceImpl(invoiceRepository, disputeRepository);
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void testImportReconciliation_WithMismatches() throws Exception {
    // Arrange
    UUID invoiceId = UUID.randomUUID();
    SalesInvoice invoice = createMockInvoice(invoiceId, "INV-001", new BigDecimal("100000"));

    when(invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceNumberIgnoreCase(
        eq(COMPANY_ID), eq(CUSTOMER_ID), eq("INV-001")))
        .thenReturn(Optional.of(invoice));

    String csvContent = "InvoiceNumber,CustomerAmount,CustomerPayment,Notes\n"
        + "INV-001,95000,30000,Discrepancy noted\n";
    MultipartFile file = new MockMultipartFile("file", "reconciliation.csv", "text/csv", csvContent.getBytes());

    // Act
    ARReconciliationImportDTO result = importService.importReconciliation(CUSTOMER_ID, file);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getMatchedCount());
    assertEquals(1, result.getMismatchCount());
    assertEquals(1, result.getMismatches().size());

    ARReconciliationImportDTO.ReconciliationMismatchDTO mismatch = result.getMismatches().get(0);
    assertEquals(invoiceId, mismatch.getInvoiceId());
    assertEquals("INV-001", mismatch.getInvoiceNumber());
    assertEquals(new BigDecimal("100000"), mismatch.getSystemAmount());
    assertEquals(new BigDecimal("95000"), mismatch.getCustomerAmount());
    assertEquals(new BigDecimal("5000"), mismatch.getVariance());
    assertEquals("SIGNIFICANT", mismatch.getVarianceType()); // > 1000 VND threshold (5000 > 1000)

    // Verify dispute was created
    ArgumentCaptor<com.accounting.entity.ARStatementDispute> disputeCaptor =
        ArgumentCaptor.forClass(com.accounting.entity.ARStatementDispute.class);
    verify(disputeRepository, times(1)).save(disputeCaptor.capture());

    com.accounting.entity.ARStatementDispute savedDispute = disputeCaptor.getValue();
    assertEquals(invoiceId, savedDispute.getInvoiceId());
    assertEquals("OPEN", savedDispute.getStatus()); // String representation
  }

  @Test
  void testImportReconciliation_SignificantVariance() throws Exception {
    // Arrange
    UUID invoiceId = UUID.randomUUID();
    SalesInvoice invoice = createMockInvoice(invoiceId, "INV-001", new BigDecimal("100000"));

    when(invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceNumberIgnoreCase(
        eq(COMPANY_ID), eq(CUSTOMER_ID), eq("INV-001")))
        .thenReturn(Optional.of(invoice));

    String csvContent = "InvoiceNumber,CustomerAmount,CustomerPayment,Notes\n"
        + "INV-001,80000,30000,Large discrepancy\n";
    MultipartFile file = new MockMultipartFile("file", "reconciliation.csv", "text/csv", csvContent.getBytes());

    // Act
    ARReconciliationImportDTO result = importService.importReconciliation(CUSTOMER_ID, file);

    // Assert
    assertEquals(1, result.getMismatchCount());
    ARReconciliationImportDTO.ReconciliationMismatchDTO mismatch = result.getMismatches().get(0);
    assertEquals("SIGNIFICANT", mismatch.getVarianceType()); // > 1000 VND threshold
  }

  @Test
  void testImportReconciliation_NoMismatches() throws Exception {
    // Arrange
    UUID invoiceId = UUID.randomUUID();
    SalesInvoice invoice = createMockInvoice(invoiceId, "INV-001", new BigDecimal("100000"));

    when(invoiceRepository.findByCompanyIdAndCustomerIdAndInvoiceNumberIgnoreCase(
        eq(COMPANY_ID), eq(CUSTOMER_ID), eq("INV-001")))
        .thenReturn(Optional.of(invoice));

    String csvContent = "InvoiceNumber,CustomerAmount,CustomerPayment,Notes\n"
        + "INV-001,100000,30000,Matches\n";
    MultipartFile file = new MockMultipartFile("file", "reconciliation.csv", "text/csv", csvContent.getBytes());

    // Act
    ARReconciliationImportDTO result = importService.importReconciliation(CUSTOMER_ID, file);

    // Assert
    assertEquals(1, result.getMatchedCount());
    assertEquals(0, result.getMismatchCount());
    verify(disputeRepository, never()).save(any());
  }

  @Test
  void testImportReconciliation_InvalidCSV() {
    // Arrange
    String csvContent = "Invalid,Header\n";
    MultipartFile file = new MockMultipartFile("file", "reconciliation.csv", "text/csv", csvContent.getBytes());

    // Act & Assert
    // The service wraps IllegalArgumentException in RuntimeException
    Exception exception = assertThrows(
        RuntimeException.class,
        () -> importService.importReconciliation(CUSTOMER_ID, file));
    assertTrue(exception.getCause() instanceof IllegalArgumentException || 
               exception.getMessage().contains("CSV must contain"));
  }

  private SalesInvoice createMockInvoice(UUID id, String invoiceNumber, BigDecimal totalAmount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(id);
    invoice.setCompanyId(COMPANY_ID);
    invoice.setCustomerId(CUSTOMER_ID);
    invoice.setInvoiceNumber(invoiceNumber);
    invoice.setInvoiceDate(LocalDate.now().minusDays(10));
    invoice.setTotalAmount(totalAmount);
    invoice.setAmountPaid(BigDecimal.ZERO);
    invoice.setRemainingBalance(totalAmount);
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    return invoice;
  }
}

