package com.accounting.service.impl.ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.InputVATReportDTO;
import com.accounting.dto.VATCorrectionCreateRequest;
import com.accounting.dto.VATCorrectionDTO;
import com.accounting.dto.VATValidationResultDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.VATCorrection;
import com.accounting.entity.VatRate;
import com.accounting.entity.VATReportHistory;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.VATCorrectionRepository;
import com.accounting.repository.VATReportHistoryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class VATServiceImplTest {

  @Mock
  private PurchaseBillLineRepository purchaseBillLineRepository;
  @Mock
  private PurchaseBillRepository purchaseBillRepository;
  @Mock
  private com.accounting.repository.SalesInvoiceLineRepository salesInvoiceLineRepository;
  @Mock
  private SupplierRepository supplierRepository;
  @Mock
  private VATCorrectionRepository vatCorrectionRepository;
  @Mock
  private VATReportHistoryRepository vatReportHistoryRepository;
  @Mock
  private PeriodManagementService periodManagementService;
  @Mock
  private AuditService auditService;

  private VATServiceImpl vatService;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    vatService = new VATServiceImpl(
        purchaseBillLineRepository,
        purchaseBillRepository,
        salesInvoiceLineRepository,
        supplierRepository,
        vatCorrectionRepository,
        vatReportHistoryRepository,
        periodManagementService,
        auditService,
        mapper);
    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void validateVATRate_shouldWarnWhenDifferentFromDefault() {
    VATValidationResultDTO result = vatService.validateVATRate(VatRate.FIVE, 1L);

    assertTrue(result.isValid());
    assertEquals(VatRate.FIVE, result.getValidatedRate());
    assertFalse(result.getWarnings().isEmpty());
  }

  @Test
  void validateVATRate_shouldErrorWhenMissing() {
    VATValidationResultDTO result = vatService.validateVATRate(null, 1L);

    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream().anyMatch(msg -> msg.contains("VAT rate is required")));
  }

  @Test
  void validateVATSum_shouldAddErrorWhenDifferenceExceedsTolerance() {
    PurchaseBill bill = createBill(new BigDecimal("0"));
    PurchaseBillLine line = createLine(bill.getId(), new BigDecimal("2000.00"));

    when(purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        eq(1L), eq(bill.getId())))
        .thenReturn(List.of(line));

    VATValidationResultDTO result = vatService.validateVATSum(bill);

    assertFalse(result.isValid());
    assertTrue(
        result.getErrors().stream()
            .anyMatch(msg -> msg.contains("exceeding tolerance of 1000")));
  }

  @Test
  void validateVATSum_shouldWarnWhenDifferenceWithinTolerance() {
    PurchaseBill bill = createBill(new BigDecimal("1500.00"));
    PurchaseBillLine line = createLine(bill.getId(), new BigDecimal("1900.00"));

    when(purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        eq(1L), eq(bill.getId())))
        .thenReturn(List.of(line));

    VATValidationResultDTO result = vatService.validateVATSum(bill);

    assertTrue(result.isValid());
    assertTrue(result.getWarnings().stream().anyMatch(msg -> msg.contains("within tolerance")));
  }

  @Test
  void validateVATRatio_shouldErrorWhenAboveHundredPercent() {
    VATValidationResultDTO result = vatService.validateVATRatio(new BigDecimal("100"), new BigDecimal("150"));

    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream().anyMatch(msg -> msg.contains("exceeds 100%")));
  }

  @Test
  void validateVATRatio_shouldErrorWhenAmountMissing() {
    VATValidationResultDTO result = vatService.validateVATRatio(BigDecimal.ZERO, new BigDecimal("10"));

    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream().anyMatch(msg -> msg.contains("Amount must be greater")));
  }

  @Test
  void generateInputVATReport_shouldAggregateLinesAndPersistHistory() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(1L);
      period.setStartDate(LocalDate.of(2025, 1, 1));
      period.setEndDate(LocalDate.of(2025, 1, 31));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      PurchaseBill bill = createBill(new BigDecimal("100000.00"));
      bill.setStatus(PurchaseBillStatus.POSTED);
      bill.setSupplierId(10L);
      bill.setBillDate(LocalDate.of(2025, 1, 10));
      bill.setDueDate(LocalDate.of(2025, 2, 10));
      bill.setTotalAmount(new BigDecimal("1100000.00"));
      bill.setVatAmount(new BigDecimal("100000.00"));

      Supplier supplier = createSupplier(10L, "SUP-001", "Alpha Supplies");
      bill.setSupplier(supplier);

      when(purchaseBillRepository.findAll(org.mockito.ArgumentMatchers.<Specification<PurchaseBill>>any()))
          .thenReturn(List.of(bill));
      PurchaseBillLine line = createLine(
          bill.getId(),
          new BigDecimal("1000000.00"),
          new BigDecimal("100000.00"),
          VatRate.TEN,
          1);
      when(purchaseBillLineRepository
          .findByCompanyIdAndPurchaseBillIdInOrderByPurchaseBillIdAscLineNumberAsc(eq(1L), anyList()))
          .thenReturn(List.of(line));

      ArgumentCaptor<VATReportHistory> historyCaptor = ArgumentCaptor.forClass(VATReportHistory.class);
      when(vatReportHistoryRepository.save(historyCaptor.capture()))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            // Simulate Hibernate generating ID if not set
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      InputVATReportDTO report = vatService.generateInputVATReport(periodId, bill.getSupplierId(), null,
          Map.of("format", "PDF"));

      assertThat(report.getItems()).hasSize(1);
      assertThat(report.getItems().get(0).getBillNumber()).isEqualTo(bill.getBillNumber());
      assertThat(report.getGrandTotalVAT()).isEqualByComparingTo("100000.00");
      assertThat(report.getGrandTotalAmount()).isEqualByComparingTo("1100000.00");
      assertThat(report.getTotalVATByRate().get(VatRate.TEN)).isEqualByComparingTo("100000.00");
      assertThat(report.getFormat()).isEqualTo(VATReportHistory.ExportFormat.PDF);

      VATReportHistory persisted = historyCaptor.getValue();
      assertThat(persisted.getCompanyId()).isEqualTo(1L);
      assertThat(persisted.getReportType()).isEqualTo(VATReportHistory.ReportType.INPUT_VAT);
      assertThat(persisted.getFormat()).isEqualTo(VATReportHistory.ExportFormat.PDF);
      assertThat(persisted.getStartDate()).isEqualTo(period.getStartDate());

      verify(auditService)
          .logVatReportGenerated(
              eq(1L),
              eq(1L),
              eq(persisted.getId()),
              eq(VATReportHistory.ReportType.INPUT_VAT.name()),
              any());
    }
  }

  @Test
  void generateInputVATReport_shouldFilterByVatClass() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(1L);
      period.setStartDate(LocalDate.of(2025, 2, 1));
      period.setEndDate(LocalDate.of(2025, 2, 28));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      PurchaseBill bill = createBill(new BigDecimal("150000.00"));
      bill.setStatus(PurchaseBillStatus.POSTED);
      bill.setSupplierId(20L);
      bill.setBillDate(LocalDate.of(2025, 2, 5));
      bill.setDueDate(LocalDate.of(2025, 3, 7));
      bill.setTotalAmount(new BigDecimal("1550000.00"));
      bill.setVatAmount(new BigDecimal("150000.00"));

      Supplier supplier = createSupplier(20L, "SUP-002", "Beta Supplies");
      bill.setSupplier(supplier);

      when(purchaseBillRepository.findAll(org.mockito.ArgumentMatchers.<Specification<PurchaseBill>>any()))
          .thenReturn(List.of(bill));
      PurchaseBillLine vatTen = createLine(
          bill.getId(),
          new BigDecimal("1000000.00"),
          new BigDecimal("100000.00"),
          VatRate.TEN,
          1);
      PurchaseBillLine vatFive = createLine(
          bill.getId(),
          new BigDecimal("500000.00"),
          new BigDecimal("50000.00"),
          VatRate.FIVE,
          2);
      when(purchaseBillLineRepository
          .findByCompanyIdAndPurchaseBillIdInOrderByPurchaseBillIdAscLineNumberAsc(eq(1L), anyList()))
          .thenReturn(List.of(vatTen, vatFive));
      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            // Simulate Hibernate generating ID if not set
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      InputVATReportDTO report = vatService.generateInputVATReport(periodId, null, VatRate.FIVE.name(), Map.of());

      assertThat(report.getItems()).hasSize(1);
      assertThat(report.getItems().get(0).getVatRate()).isEqualTo(VatRate.FIVE);
      assertThat(report.getGrandTotalVAT()).isEqualByComparingTo("50000.00");
      assertThat(report.getTotalVATByRate().get(VatRate.FIVE)).isEqualByComparingTo("50000.00");
      assertThat(report.getTotalVATByRate().getOrDefault(VatRate.TEN, BigDecimal.ZERO))
          .isEqualByComparingTo(BigDecimal.ZERO);

      verify(auditService)
          .logVatReportGenerated(
              eq(1L), eq(1L), any(UUID.class), eq(VATReportHistory.ReportType.INPUT_VAT.name()), any());
    }
  }

  @Test
  void generateInputVATReport_shouldFormatND123CompliantData() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(1L);
      period.setStartDate(LocalDate.of(2025, 3, 1));
      period.setEndDate(LocalDate.of(2025, 3, 31));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      PurchaseBill bill1 = createBill(new BigDecimal("50000.00"));
      bill1.setStatus(PurchaseBillStatus.POSTED);
      bill1.setSupplierId(30L);
      bill1.setBillDate(LocalDate.of(2025, 3, 5));
      bill1.setTotalAmount(new BigDecimal("550000.00"));
      bill1.setVatAmount(new BigDecimal("50000.00"));

      PurchaseBill bill2 = createBill(new BigDecimal("25000.00"));
      bill2.setStatus(PurchaseBillStatus.POSTED);
      bill2.setSupplierId(30L);
      bill2.setBillDate(LocalDate.of(2025, 3, 10));
      bill2.setTotalAmount(new BigDecimal("275000.00"));
      bill2.setVatAmount(new BigDecimal("25000.00"));

      Supplier supplier = createSupplier(30L, "SUP-003", "Gamma Supplies");
      bill1.setSupplier(supplier);
      bill2.setSupplier(supplier);

      when(purchaseBillRepository.findAll(org.mockito.ArgumentMatchers.<Specification<PurchaseBill>>any()))
          .thenReturn(List.of(bill1, bill2));

      PurchaseBillLine line1 = createLine(bill1.getId(), new BigDecimal("500000.00"), new BigDecimal("50000.00"),
          VatRate.TEN, 1);
      PurchaseBillLine line2 = createLine(bill2.getId(), new BigDecimal("250000.00"), new BigDecimal("25000.00"),
          VatRate.TEN, 1);

      when(purchaseBillLineRepository
          .findByCompanyIdAndPurchaseBillIdInOrderByPurchaseBillIdAscLineNumberAsc(eq(1L), anyList()))
          .thenReturn(List.of(line1, line2));
      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            // Simulate Hibernate generating ID if not set
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      InputVATReportDTO report = vatService.generateInputVATReport(periodId, null, null, Map.of());

      assertThat(report.getItems()).hasSize(2);
      assertThat(report.getGrandTotalVAT()).isEqualByComparingTo("75000.00");
      assertThat(report.getGrandTotalAmount()).isEqualByComparingTo("825000.00");
      assertThat(report.getTotalVATByRate().get(VatRate.TEN)).isEqualByComparingTo("75000.00");
      assertThat(report.getStartDate()).isEqualTo(period.getStartDate());
      assertThat(report.getEndDate()).isEqualTo(period.getEndDate());
    }
  }

  @Test
  void createVATCorrection_shouldCreatePendingCorrection() {
    // Setup security context
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    when(auth.getPrincipal()).thenReturn("1");
    java.util.List<SimpleGrantedAuthority> authorities = java.util.List
        .of(new SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT"));
    doReturn(authorities).when(auth).getAuthorities();
    SecurityContextHolder.setContext(securityContext);

    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

      PurchaseBill bill = createBill(new BigDecimal("10000.00"));
      bill.setStatus(PurchaseBillStatus.POSTED);
      when(purchaseBillRepository.findByCompanyIdAndId(eq(1L), eq(bill.getId())))
          .thenReturn(Optional.of(bill));

      PurchaseBillLine line = createLine(bill.getId(), new BigDecimal("100000.00"), new BigDecimal("10000.00"),
          VatRate.TEN, 1);
      when(purchaseBillLineRepository.findByCompanyIdAndId(eq(1L), eq(line.getId())))
          .thenReturn(Optional.of(line));

      VATCorrectionCreateRequest request = new VATCorrectionCreateRequest();
      request.setPurchaseBillId(bill.getId());
      request.setPurchaseBillLineId(line.getId());
      request.setNewVatAmount(new BigDecimal("15000.00"));
      request.setReason("Correction due to supplier error");

      VATCorrection saved = new VATCorrection();
      saved.setId(UUID.randomUUID());
      saved.setCompanyId(1L);
      saved.setPurchaseBillId(bill.getId());
      saved.setPurchaseBillLineId(line.getId());
      saved.setOldVatAmount(new BigDecimal("10000.00"));
      saved.setNewVatAmount(new BigDecimal("15000.00"));
      saved.setReason(request.getReason());
      saved.setStatus(VATCorrection.Status.PENDING);
      saved.setCorrectedById(1L);
      saved.setCorrectedAt(Instant.now());

      when(vatCorrectionRepository.save(any(VATCorrection.class))).thenReturn(saved);

      VATCorrectionDTO result = vatService.createVATCorrection(request);

      assertThat(result.getPurchaseBillId()).isEqualTo(bill.getId());
      assertThat(result.getPurchaseBillLineId()).isEqualTo(line.getId());
      assertThat(result.getOldVatAmount()).isEqualByComparingTo("10000.00");
      assertThat(result.getNewVatAmount()).isEqualByComparingTo("15000.00");
      assertThat(result.getDifference()).isEqualByComparingTo("5000.00");
      assertThat(result.getStatus()).isEqualTo(VATCorrection.Status.PENDING);
      assertThat(result.getReason()).isEqualTo(request.getReason());

      verify(auditService).logVatCorrectionCreated(
          eq(1L), eq(1L), any(UUID.class), eq(bill.getId()),
          eq(new BigDecimal("10000.00")), eq(new BigDecimal("15000.00")), eq(request.getReason()));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void createVATCorrection_shouldRejectWhenNewAmountEqualsOld() {
    // Setup security context
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    when(auth.getPrincipal()).thenReturn("1");
    java.util.List<SimpleGrantedAuthority> authorities = java.util.List
        .of(new SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT"));
    doReturn(authorities).when(auth).getAuthorities();
    SecurityContextHolder.setContext(securityContext);

    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

      PurchaseBill bill = createBill(new BigDecimal("10000.00"));
      when(purchaseBillRepository.findByCompanyIdAndId(eq(1L), eq(bill.getId())))
          .thenReturn(Optional.of(bill));

      VATCorrectionCreateRequest request = new VATCorrectionCreateRequest();
      request.setPurchaseBillId(bill.getId());
      request.setNewVatAmount(new BigDecimal("10000.00"));
      request.setReason("No change needed");

      org.junit.jupiter.api.Assertions.assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> vatService.createVATCorrection(request));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void approveVATCorrection_shouldApplyCorrectionAndUpdateBill() {
    // Setup security context
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    when(auth.getPrincipal()).thenReturn("2");
    java.util.List<SimpleGrantedAuthority> authorities = java.util.List
        .of(new SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT"));
    doReturn(authorities).when(auth).getAuthorities();
    SecurityContextHolder.setContext(securityContext);

    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(2L);

      PurchaseBill bill = createBill(new BigDecimal("10000.00"));
      bill.setStatus(PurchaseBillStatus.DRAFT);
      when(purchaseBillRepository.findByCompanyIdAndId(eq(1L), eq(bill.getId())))
          .thenReturn(Optional.of(bill));

      PurchaseBillLine line = createLine(bill.getId(), new BigDecimal("100000.00"), new BigDecimal("10000.00"),
          VatRate.TEN, 1);
      when(purchaseBillLineRepository.findByCompanyIdAndId(eq(1L), eq(line.getId())))
          .thenReturn(Optional.of(line));

      VATCorrection correction = new VATCorrection();
      correction.setId(UUID.randomUUID());
      correction.setCompanyId(1L);
      correction.setPurchaseBillId(bill.getId());
      correction.setPurchaseBillLineId(line.getId());
      correction.setOldVatAmount(new BigDecimal("10000.00"));
      correction.setNewVatAmount(new BigDecimal("15000.00"));
      correction.setReason("Supplier correction");
      correction.setStatus(VATCorrection.Status.PENDING);
      correction.setCorrectedById(1L);
      correction.setCorrectedAt(Instant.now());

      when(vatCorrectionRepository.findByCompanyIdAndId(eq(1L), eq(correction.getId())))
          .thenReturn(Optional.of(correction));
      when(vatCorrectionRepository.save(any(VATCorrection.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(purchaseBillRepository.save(any(PurchaseBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(purchaseBillLineRepository.save(any(PurchaseBillLine.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      VATCorrectionDTO result = vatService.approveVATCorrection(correction.getId(), null);

      assertThat(result.getStatus()).isEqualTo(VATCorrection.Status.APPROVED);
      assertThat(result.getApprovedById()).isEqualTo(2L);
      assertThat(result.getApprovedAt()).isNotNull();

      verify(purchaseBillLineRepository)
          .save(argThat(l -> l.getVatAmount().compareTo(new BigDecimal("15000.00")) == 0));
      verify(auditService).logVatCorrectionApproved(
          eq(1L), eq(2L), eq(correction.getId()), eq(bill.getId()),
          eq(new BigDecimal("10000.00")), eq(new BigDecimal("15000.00")));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void approveVATCorrection_shouldRejectWhenNotPending() {
    // Setup security context
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    when(auth.getPrincipal()).thenReturn("2");
    java.util.List<SimpleGrantedAuthority> authorities = java.util.List
        .of(new SimpleGrantedAuthority("ROLE_CHIEF_ACCOUNTANT"));
    doReturn(authorities).when(auth).getAuthorities();
    SecurityContextHolder.setContext(securityContext);

    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock = org.mockito.Mockito
        .mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(2L);

      VATCorrection correction = new VATCorrection();
      correction.setId(UUID.randomUUID());
      correction.setCompanyId(1L);
      correction.setStatus(VATCorrection.Status.APPROVED);

      when(vatCorrectionRepository.findByCompanyIdAndId(eq(1L), eq(correction.getId())))
          .thenReturn(Optional.of(correction));

      org.junit.jupiter.api.Assertions.assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> vatService.approveVATCorrection(correction.getId(), null));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private PurchaseBill createBill(BigDecimal documentVat) {
    PurchaseBill bill = new PurchaseBill();
    bill.setId(UUID.randomUUID());
    bill.setCompanyId(1L);
    bill.setSupplierId(10L);
    bill.setBillNumber("BILL-001");
    bill.setBillDate(LocalDate.now());
    bill.setDueDate(LocalDate.now().plusDays(5));
    bill.setReference("REF");
    bill.setStatus(PurchaseBillStatus.DRAFT);
    bill.setTotalAmount(new BigDecimal("5000"));
    bill.setVatAmount(documentVat);
    bill.setCreatedById(1L);
    bill.setIsSensitive(false);
    return bill;
  }

  private PurchaseBillLine createLine(UUID billId, BigDecimal vatAmount) {
    return createLine(billId, new BigDecimal("1000.00"), vatAmount, VatRate.TEN, 1);
  }

  private PurchaseBillLine createLine(
      UUID billId, BigDecimal baseAmount, BigDecimal vatAmount, VatRate vatRate, int lineNumber) {
    PurchaseBillLine line = new PurchaseBillLine();
    line.setId(UUID.randomUUID());
    line.setCompanyId(1L);
    line.setPurchaseBillId(billId);
    line.setLineNumber(lineNumber);
    line.setAccountId(100L);
    line.setDescription("Line " + lineNumber);
    line.setAmount(baseAmount);
    line.setUnitPrice(baseAmount);
    line.setQuantity(BigDecimal.ONE);
    line.setVatAmount(vatAmount);
    line.setVatRate(vatRate);
    return line;
  }

  private Supplier createSupplier(Long id, String code, String name) {
    Supplier supplier = new Supplier();
    supplier.setId(id);
    supplier.setCompanyId(1L);
    supplier.setCode(code);
    supplier.setName(name);
    supplier.setTaxCode("0123456789");
    supplier.setAddress("Test address");
    return supplier;
  }
}
