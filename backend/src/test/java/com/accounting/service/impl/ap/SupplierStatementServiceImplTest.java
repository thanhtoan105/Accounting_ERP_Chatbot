package com.accounting.service.impl.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.DetailedStatementDTO;
import com.accounting.dto.ReconciliationResultDTO;
import com.accounting.dto.SupplierStatementDTO;
import com.accounting.entity.*;
import com.accounting.repository.*;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for SupplierStatementServiceImpl.
 * Tests statement generation, export, import, reconciliation, and dispute
 * management.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SupplierStatementServiceImplTest {

  @Mock
  private PurchaseBillRepository billRepository;
  @Mock
  private APPaymentRepository paymentRepository;
  @Mock
  private PaymentAllocationRepository allocationRepository;
  @Mock
  private SupplierRepository supplierRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private SupplierStatementHistoryRepository historyRepository;
  @Mock
  private SupplierStatementDisputeRepository disputeRepository;
  @Mock
  private AuditService auditService;
  @Mock
  private ObjectMapper objectMapper;

  @InjectMocks
  private SupplierStatementServiceImpl statementService;

  private static final Long COMPANY_ID = 1L;
  private static final Long SUPPLIER_ID = 100L;
  private static final Long USER_ID = 10L;
  private LocalDate startDate;
  private LocalDate endDate;
  private Supplier supplier;
  private User user;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
    startDate = LocalDate.now().minusMonths(1);
    endDate = LocalDate.now();

    // Setup supplier
    supplier = new Supplier();
    supplier.setId(SUPPLIER_ID);
    supplier.setCompanyId(COMPANY_ID);
    supplier.setName("Test Supplier");
    supplier.setCode("SUP001");

    // Setup user
    user = new User();
    user.setId(USER_ID);
    user.setEmail("test@example.com");
    user.setFullName("Test User");

    // Setup SecurityContext
    org.springframework.security.core.context.SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder
        .createEmptyContext();
    org.springframework.security.core.Authentication authentication = org.mockito.Mockito
        .mock(org.springframework.security.core.Authentication.class);
    when(authentication.getName()).thenReturn("test@example.com");
    when(authentication.isAuthenticated()).thenReturn(true);
    securityContext.setAuthentication(authentication);
    org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

    // Mock user repository
    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    when(supplierRepository.findByCompanyIdAndId(COMPANY_ID, SUPPLIER_ID))
        .thenReturn(Optional.of(supplier));
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void generateSummaryStatement_withValidData_returnsStatementWithHistory() {
    // Setup
    PurchaseBill bill = createPurchaseBill(
        startDate.plusDays(5), new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(bill));
    when(allocationRepository.findByPurchaseBillId(bill.getId())).thenReturn(Collections.emptyList());
    when(historyRepository.save(any(SupplierStatementHistory.class)))
        .thenAnswer(invocation -> {
          SupplierStatementHistory history = invocation.getArgument(0);
          if (history.getId() == null) {
            history.setId(UUID.randomUUID());
          }
          return history;
        });

    // Execute
    SupplierStatementDTO result = statementService.generateSummaryStatement(SUPPLIER_ID, startDate, endDate);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getSupplierId()).isEqualTo(SUPPLIER_ID);
    assertThat(result.getSupplierName()).isEqualTo("Test Supplier");
    assertThat(result.getStatementType())
        .isEqualTo(SupplierStatementHistory.StatementType.SUMMARY);
    assertThat(result.getItems()).isNotEmpty();
    assertThat(result.getId()).isNotNull(); // Should have persisted ID
    assertThat(result.getTotalDebits()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.getOpeningBalance()).isNotNull();
    assertThat(result.getClosingBalance()).isNotNull();

    // Verify history was saved
    verify(historyRepository, times(1)).save(any(SupplierStatementHistory.class));
  }

  @Test
  void generateSummaryStatement_withPayments_includesPaymentLines() {
    // Setup
    PurchaseBill bill = createPurchaseBill(
        startDate.plusDays(5), new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    APPayment payment = createPayment(startDate.plusDays(10), new BigDecimal("500.00"));
    PaymentAllocation allocation = createAllocation(bill, payment, new BigDecimal("500.00"));

    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(bill));
    when(allocationRepository.findByPurchaseBillId(bill.getId())).thenReturn(List.of(allocation));
    when(historyRepository.save(any(SupplierStatementHistory.class)))
        .thenAnswer(invocation -> {
          SupplierStatementHistory history = invocation.getArgument(0);
          history.setId(UUID.randomUUID());
          return history;
        });

    // Execute
    SupplierStatementDTO result = statementService.generateSummaryStatement(SUPPLIER_ID, startDate, endDate);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getItems()).hasSize(2); // Bill + Payment
    assertThat(result.getTotalDebits()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.getTotalCredits()).isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat(result.getClosingBalance())
        .isEqualByComparingTo(result.getOpeningBalance().add(new BigDecimal("500.00")));
  }

  @Test
  void generateDetailedStatement_withValidData_returnsDetailedStatement() {
    // Setup
    PurchaseBill bill = createPurchaseBill(
        startDate.plusDays(5), new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(bill));
    when(billRepository.findById(bill.getId())).thenReturn(Optional.of(bill));
    when(allocationRepository.findByPurchaseBillId(bill.getId())).thenReturn(Collections.emptyList());
    when(historyRepository.save(any(SupplierStatementHistory.class)))
        .thenAnswer(invocation -> {
          SupplierStatementHistory history = invocation.getArgument(0);
          history.setId(UUID.randomUUID());
          return history;
        });

    // Execute
    DetailedStatementDTO result = statementService.generateDetailedStatement(SUPPLIER_ID, startDate, endDate);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getStatementType())
        .isEqualTo(SupplierStatementHistory.StatementType.DETAILED);
    assertThat(result.getBillDetails()).isNotEmpty();
    assertThat(result.getId()).isNotNull(); // Should have persisted ID
    assertThat(result.getBillDetails().get(0).getBillId()).isEqualTo(bill.getId());
    assertThat(result.getBillDetails().get(0).getTotalAmount())
        .isEqualByComparingTo(new BigDecimal("1000.00"));

    // Verify history was saved
    verify(historyRepository, times(1)).save(any(SupplierStatementHistory.class));
    // Verify calculateRemainingBalance was called (via billRepository.findById)
    verify(billRepository, atLeastOnce()).findById(bill.getId());
  }

  @Test
  void generateSummaryStatement_withMissingCompanyContext_throwsException() {
    // Setup
    CompanyContext.clear();

    // Execute & Assert
    assertThatThrownBy(
        () -> statementService.generateSummaryStatement(SUPPLIER_ID, startDate, endDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing company context");
  }

  @Test
  void generateSummaryStatement_withInvalidSupplier_throwsException() {
    // Setup
    when(supplierRepository.findByCompanyIdAndId(COMPANY_ID, SUPPLIER_ID))
        .thenReturn(Optional.empty());

    // Execute & Assert
    assertThatThrownBy(
        () -> statementService.generateSummaryStatement(SUPPLIER_ID, startDate, endDate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplier not found");
  }

  @Test
  void exportStatement_withValidId_returnsExcelData() {
    // Setup
    UUID statementId = UUID.randomUUID();
    SupplierStatementHistory history = createStatementHistory(statementId);
    when(historyRepository.findByCompanyIdAndId(COMPANY_ID, statementId))
        .thenReturn(Optional.of(history));
    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(Collections.emptyList());
    when(historyRepository.save(any(SupplierStatementHistory.class))).thenReturn(history);

    // Execute
    byte[] result = statementService.exportStatement(
        statementId, SupplierStatementHistory.ExportFormat.EXCEL);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.length).isGreaterThan(0);
    verify(historyRepository, times(1)).save(history);
    assertThat(history.getDownloadCount()).isEqualTo(1);
  }

  @Test
  void exportStatement_withValidId_returnsPdfData() {
    // Setup
    UUID statementId = UUID.randomUUID();
    SupplierStatementHistory history = createStatementHistory(statementId);
    when(historyRepository.findByCompanyIdAndId(COMPANY_ID, statementId))
        .thenReturn(Optional.of(history));
    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(Collections.emptyList());
    when(historyRepository.save(any(SupplierStatementHistory.class))).thenReturn(history);

    // Execute
    byte[] result = statementService.exportStatement(statementId, SupplierStatementHistory.ExportFormat.PDF);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.length).isGreaterThan(0);
    verify(historyRepository, times(1)).save(history);
  }

  @Test
  void sendStatementToSupplier_updatesHistoryAndLogs() throws Exception {
    // Setup
    UUID statementId = UUID.randomUUID();
    SupplierStatementHistory history = createStatementHistory(statementId);
    List<String> recipientEmails = List.of("supplier@example.com", "accounting@example.com");

    when(historyRepository.findByCompanyIdAndId(COMPANY_ID, statementId))
        .thenReturn(Optional.of(history));
    when(historyRepository.save(any(SupplierStatementHistory.class))).thenReturn(history);
    when(objectMapper.writeValueAsString(any())).thenReturn("[\"supplier@example.com\"]");

    // Execute
    statementService.sendStatementToSupplier(statementId, recipientEmails);

    // Assert
    verify(historyRepository, times(1)).save(history);
    assertThat(history.getSentDate()).isNotNull();
    assertThat(history.getSentTo()).isNotNull();
  }

  @Test
  void importSupplierStatement_withValidExcel_returnsReconciliationResult() throws Exception {
    // Setup - Create a minimal valid Excel file
    org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
    org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Statement");
    org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue("Bill Number");
    headerRow.createCell(1).setCellValue("Bill Date");
    headerRow.createCell(2).setCellValue("Amount");

    org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(1);
    dataRow.createCell(0).setCellValue("BILL001");
    dataRow.createCell(1).setCellValue("01/01/2024");
    dataRow.createCell(2).setCellValue(1000.00);

    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    workbook.write(baos);
    workbook.close();

    MultipartFile file = new MockMultipartFile(
        "file", "statement.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        baos.toByteArray());

    PurchaseBill systemBill = createPurchaseBill(
        LocalDate.of(2024, 1, 1), new BigDecimal("1000.00"), PurchaseBillStatus.POSTED);
    systemBill.setBillNumber("BILL001");

    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(systemBill));

    // Execute
    ReconciliationResultDTO result = statementService.importSupplierStatement(SUPPLIER_ID, file, "EXCEL");

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getSupplierId()).isEqualTo(SUPPLIER_ID);
  }

  @Test
  void saveReconciliationResults_createsDisputesForMismatches() {
    // Setup
    ReconciliationResultDTO results = new ReconciliationResultDTO();
    results.setSupplierId(SUPPLIER_ID);
    results.setMismatchedCount(2);
    results.setMissingCount(1);

    ReconciliationResultDTO.ReconciliationItemDTO mismatch1 = new ReconciliationResultDTO.ReconciliationItemDTO();
    mismatch1.setBillNumber("BILL001");
    mismatch1.setSupplierAmount(new BigDecimal("1000.00"));
    mismatch1.setSystemAmount(new BigDecimal("900.00"));
    mismatch1.setStatus("MISMATCHED");
    mismatch1.setNotes("Amount difference");

    ReconciliationResultDTO.ReconciliationItemDTO mismatch2 = new ReconciliationResultDTO.ReconciliationItemDTO();
    mismatch2.setBillNumber("BILL002");
    mismatch2.setSupplierAmount(new BigDecimal("2000.00"));
    mismatch2.setSystemAmount(new BigDecimal("1900.00"));
    mismatch2.setStatus("MISMATCHED");

    ReconciliationResultDTO.ReconciliationItemDTO missing = new ReconciliationResultDTO.ReconciliationItemDTO();
    missing.setBillNumber("BILL999");
    missing.setSupplierAmount(new BigDecimal("500.00"));
    missing.setStatus("MISSING");
    missing.setNotes("Bill not found in system");

    results.setMismatched(List.of(mismatch1, mismatch2));
    results.setMissing(List.of(missing));

    when(disputeRepository.save(any(SupplierStatementDispute.class)))
        .thenAnswer(invocation -> {
          SupplierStatementDispute dispute = invocation.getArgument(0);
          if (dispute.getId() == null) {
            dispute.setId(UUID.randomUUID());
          }
          return dispute;
        });

    // Execute
    statementService.saveReconciliationResults(SUPPLIER_ID, results, "Test notes");

    // Assert
    verify(disputeRepository, times(3)).save(any(SupplierStatementDispute.class)); // 2 mismatches + 1 missing
  }

  @Test
  void updateDisputeLog_withResolvedStatus_updatesDispute() {
    // Setup
    UUID disputeId = UUID.randomUUID();
    SupplierStatementDispute dispute = new SupplierStatementDispute();
    dispute.setId(disputeId);
    dispute.setCompanyId(COMPANY_ID);
    dispute.setSupplierId(SUPPLIER_ID);
    dispute.setStatusEnum(SupplierStatementDispute.DisputeStatus.OPEN);
    dispute.setDisputeReason("Amount mismatch");

    com.accounting.dto.UpdateDisputeRequest request = new com.accounting.dto.UpdateDisputeRequest();
    request.setStatus(SupplierStatementDispute.DisputeStatus.RESOLVED);
    request.setResolutionNotes("Resolved by adjusting amount");

    when(disputeRepository.findByCompanyIdAndId(COMPANY_ID, disputeId))
        .thenReturn(Optional.of(dispute));
    when(disputeRepository.save(any(SupplierStatementDispute.class))).thenReturn(dispute);

    // Execute
    statementService.updateDisputeLog(disputeId, request);

    // Assert
    verify(disputeRepository, times(1)).save(dispute);
    assertThat(dispute.getStatusEnum()).isEqualTo(SupplierStatementDispute.DisputeStatus.RESOLVED);
    assertThat(dispute.getResolvedById()).isEqualTo(USER_ID);
    assertThat(dispute.getResolvedAt()).isNotNull();
  }

  @Test
  void findAllStatements_withFilters_returnsFilteredResults() {
    // Setup
    SupplierStatementHistory history1 = createStatementHistory(UUID.randomUUID());
    SupplierStatementHistory history2 = createStatementHistory(UUID.randomUUID());
    Page<SupplierStatementHistory> page = new PageImpl<>(List.of(history1, history2), PageRequest.of(0, 20), 2);

    when(historyRepository.findByCompanyIdAndPartyIdAndStatementType(
        COMPANY_ID, SUPPLIER_ID, "SUMMARY",
        PageRequest.of(0, 20)))
        .thenReturn(page);

    // Execute
    Page<com.accounting.dto.SupplierStatementHistoryDTO> result = statementService.findAllStatements(
        SUPPLIER_ID,
        null,
        null,
        SupplierStatementHistory.StatementType.SUMMARY,
        PageRequest.of(0, 20));

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void downloadStatementBatch_withMultipleStatements_returnsZipData() {
    // Setup
    UUID statementId1 = UUID.randomUUID();
    UUID statementId2 = UUID.randomUUID();
    SupplierStatementHistory history1 = createStatementHistory(statementId1);
    history1.setEndDate(endDate.minusDays(1)); // Different date to avoid duplicate filenames
    history1.setSupplier(supplier);
    SupplierStatementHistory history2 = createStatementHistory(statementId2);
    history2.setSupplier(supplier);

    when(historyRepository.findByCompanyIdAndIdIn(COMPANY_ID, List.of(statementId1, statementId2)))
        .thenReturn(List.of(history1, history2));
    when(billRepository.findByCompanyId(COMPANY_ID)).thenReturn(Collections.emptyList());

    // Execute
    byte[] result = statementService.downloadStatementBatch(List.of(statementId1, statementId2));

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.length).isGreaterThan(0);
  }

  // Helper methods
  private PurchaseBill createPurchaseBill(
      LocalDate billDate, BigDecimal totalAmount, PurchaseBillStatus status) {
    PurchaseBill bill = new PurchaseBill();
    bill.setId(UUID.randomUUID());
    bill.setCompanyId(COMPANY_ID);
    bill.setSupplierId(SUPPLIER_ID);
    bill.setBillDate(billDate);
    bill.setDueDate(billDate.plusDays(30));
    bill.setTotalAmount(totalAmount);
    bill.setStatus(status);
    bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8));
    bill.setReference("REF-" + bill.getBillNumber());
    bill.setDescription("Test bill");
    return bill;
  }

  private APPayment createPayment(LocalDate paymentDate, BigDecimal amount) {
    APPayment payment = new APPayment();
    payment.setId(UUID.randomUUID());
    payment.setCompanyId(COMPANY_ID);
    payment.setSupplierId(SUPPLIER_ID);
    payment.setPaymentDate(paymentDate);
    payment.setAmount(amount);
    payment.setPaymentNumber("PAY-" + UUID.randomUUID().toString().substring(0, 8));
    payment.setReference("PAY-REF");
    return payment;
  }

  private PaymentAllocation createAllocation(
      PurchaseBill bill, APPayment payment, BigDecimal allocatedAmount) {
    PaymentAllocation allocation = new PaymentAllocation();
    allocation.setId(UUID.randomUUID());
    allocation.setPurchaseBill(bill);
    allocation.setPayment(payment);
    allocation.setAllocatedAmount(allocatedAmount);
    return allocation;
  }

  private SupplierStatementHistory createStatementHistory(UUID id) {
    SupplierStatementHistory history = new SupplierStatementHistory();
    history.setId(id);
    history.setCompanyId(COMPANY_ID);
    history.setSupplierId(SUPPLIER_ID);
    history.setStatementTypeEnum(SupplierStatementHistory.StatementType.SUMMARY);
    history.setGenerationDate(Instant.now());
    history.setGeneratedById(USER_ID);
    history.setExportFormatEnum(SupplierStatementHistory.ExportFormat.EXCEL);
    history.setHash("test-hash");
    history.setStartDate(startDate);
    history.setEndDate(endDate);
    history.setViewCount(0);
    history.setDownloadCount(0);
    return history;
  }
}
