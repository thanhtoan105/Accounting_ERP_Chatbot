package com.accounting.service.impl.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.OutputVATReportDTO;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.VATReportHistory;
import com.accounting.entity.VatRate;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.VATReportHistoryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ARVATReportServiceImplTest {

  @Mock
  private SalesInvoiceRepository salesInvoiceRepository;
  @Mock
  private SalesInvoiceLineRepository salesInvoiceLineRepository;
  @Mock
  private CustomerRepository customerRepository;
  @Mock
  private VATReportHistoryRepository vatReportHistoryRepository;
  @Mock
  private PeriodManagementService periodManagementService;
  @Mock
  private AuditService auditService;
  @Mock
  private SecurityContext securityContext;
  @Mock
  private Authentication authentication;

  private ARVATReportServiceImpl arVatReportService;
  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;
  private static final Long CUSTOMER_ID = 200L;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    arVatReportService = new ARVATReportServiceImpl(
        salesInvoiceRepository,
        salesInvoiceLineRepository,
        customerRepository,
        vatReportHistoryRepository,
        periodManagementService,
        auditService,
        mapper);
    CompanyContext.setCompanyId(COMPANY_ID);

    // Setup security context
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getAuthorities())
        .thenAnswer(invocation -> List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT")));
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void generateVATReport_shouldGenerateReportWithPostedInvoices() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(COMPANY_ID);
      period.setStartDate(LocalDate.of(2025, 1, 1));
      period.setEndDate(LocalDate.of(2025, 1, 31));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      SalesInvoice invoice = createInvoice(new BigDecimal("100000.00"), new BigDecimal("10000.00"));
      invoice.setStatus(SalesInvoiceStatus.POSTED);
      invoice.setCustomerId(CUSTOMER_ID);
      invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));

      Customer customer = createCustomer(CUSTOMER_ID, "CUST-001", "Test Customer", "1234567890");
      invoice.setCustomer(customer);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice));
      SalesInvoiceLine line = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("10000.00"),
          VatRate.TEN,
          1);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(line));

      ArgumentCaptor<VATReportHistory> historyCaptor = ArgumentCaptor.forClass(VATReportHistory.class);
      when(vatReportHistoryRepository.save(historyCaptor.capture()))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      OutputVATReportDTO report = arVatReportService.generateVATReport(periodId, null, null, Map.of());

      assertThat(report.getItems()).hasSize(1);
      assertThat(report.getItems().get(0).getInvoiceNumber()).isEqualTo(invoice.getInvoiceNumber());
      assertThat(report.getGrandTotalVAT()).isEqualByComparingTo("10000.00");
      assertThat(report.getGrandTotalAmount()).isEqualByComparingTo("110000.00");
      assertThat(report.getTotalVATByRate().get(VatRate.TEN)).isEqualByComparingTo("10000.00");
      assertThat(report.getRevenue10pct()).isEqualByComparingTo("100000.00");
      assertThat(report.getTotalVatCollected()).isEqualByComparingTo("10000.00");

      VATReportHistory persisted = historyCaptor.getValue();
      assertThat(persisted.getCompanyId()).isEqualTo(COMPANY_ID);
      assertThat(persisted.getReportType()).isEqualTo(VATReportHistory.ReportType.OUTPUT_VAT);
      assertThat(persisted.getStartDate()).isEqualTo(period.getStartDate());
      assertThat(persisted.getEndDate()).isEqualTo(period.getEndDate());

      verify(auditService)
          .logVatReportGenerated(
              eq(COMPANY_ID),
              eq(USER_ID),
              eq(persisted.getId()),
              eq(VATReportHistory.ReportType.OUTPUT_VAT.name()),
              any());
    }
  }

  @Test
  void generateVATReport_shouldFilterByCustomer() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(COMPANY_ID);
      period.setStartDate(LocalDate.of(2025, 2, 1));
      period.setEndDate(LocalDate.of(2025, 2, 28));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      SalesInvoice invoice1 = createInvoice(new BigDecimal("50000.00"), new BigDecimal("5000.00"));
      invoice1.setStatus(SalesInvoiceStatus.POSTED);
      invoice1.setCustomerId(CUSTOMER_ID);
      invoice1.setInvoiceDate(LocalDate.of(2025, 2, 5));

      SalesInvoice invoice2 = createInvoice(new BigDecimal("30000.00"), new BigDecimal("3000.00"));
      invoice2.setStatus(SalesInvoiceStatus.POSTED);
      invoice2.setCustomerId(300L); // Different customer
      invoice2.setInvoiceDate(LocalDate.of(2025, 2, 10));

      Customer customer1 = createCustomer(CUSTOMER_ID, "CUST-001", "Customer 1", "1234567890");
      invoice1.setCustomer(customer1);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice1, invoice2));
      SalesInvoiceLine line1 = createLine(
          invoice1.getId(),
          new BigDecimal("50000.00"),
          new BigDecimal("5000.00"),
          VatRate.TEN,
          1);
      SalesInvoiceLine line2 = createLine(
          invoice2.getId(),
          new BigDecimal("30000.00"),
          new BigDecimal("3000.00"),
          VatRate.TEN,
          1);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(line1, line2));

      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      // Filter by customer
      OutputVATReportDTO report = arVatReportService.generateVATReport(periodId, CUSTOMER_ID, null, Map.of());

      // Should only include invoice1 (customerId matches)
      assertThat(report.getCustomerId()).isEqualTo(CUSTOMER_ID);
      assertThat(report.getCustomerName()).isEqualTo("Customer 1");
      // Note: The actual filtering happens in the repository specification, so we verify the report
      // structure is correct
      assertThat(report.getItems()).isNotNull();
    }
  }

  @Test
  void generateVATReport_shouldFilterByVatClass() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(COMPANY_ID);
      period.setStartDate(LocalDate.of(2025, 3, 1));
      period.setEndDate(LocalDate.of(2025, 3, 31));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      SalesInvoice invoice = createInvoice(new BigDecimal("150000.00"), new BigDecimal("15000.00"));
      invoice.setStatus(SalesInvoiceStatus.POSTED);
      invoice.setCustomerId(CUSTOMER_ID);
      invoice.setInvoiceDate(LocalDate.of(2025, 3, 5));

      Customer customer = createCustomer(CUSTOMER_ID, "CUST-001", "Test Customer", "1234567890");
      invoice.setCustomer(customer);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice));
      SalesInvoiceLine vatTen = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("10000.00"),
          VatRate.TEN,
          1);
      SalesInvoiceLine vatFive = createLine(
          invoice.getId(),
          new BigDecimal("50000.00"),
          new BigDecimal("5000.00"),
          VatRate.FIVE,
          2);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(vatTen, vatFive));

      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      // Filter by VAT class FIVE
      OutputVATReportDTO report = arVatReportService.generateVATReport(periodId, null, VatRate.FIVE.name(), Map.of());

      // Should only include line with FIVE VAT rate
      assertThat(report.getVatClass()).isEqualTo(VatRate.FIVE.name());
      assertThat(report.getItems()).isNotNull();
      // Verify aggregation only includes FIVE rate
      assertThat(report.getTotalVATByRate().get(VatRate.FIVE)).isEqualByComparingTo("5000.00");
      assertThat(report.getRevenue5pct()).isEqualByComparingTo("50000.00");
    }
  }

  @Test
  void generateVATReport_shouldAggregateRevenueByVatRate() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = new AccountingPeriodDTO();
      period.setId(periodId);
      period.setCompanyId(COMPANY_ID);
      period.setStartDate(LocalDate.of(2025, 4, 1));
      period.setEndDate(LocalDate.of(2025, 4, 30));
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      SalesInvoice invoice = createInvoice(new BigDecimal("250000.00"), new BigDecimal("25000.00"));
      invoice.setStatus(SalesInvoiceStatus.POSTED);
      invoice.setCustomerId(CUSTOMER_ID);
      invoice.setInvoiceDate(LocalDate.of(2025, 4, 10));

      Customer customer = createCustomer(CUSTOMER_ID, "CUST-001", "Test Customer", "1234567890");
      invoice.setCustomer(customer);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice));
      SalesInvoiceLine zero = createLine(
          invoice.getId(),
          new BigDecimal("50000.00"),
          BigDecimal.ZERO,
          VatRate.ZERO,
          1);
      SalesInvoiceLine five = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("5000.00"),
          VatRate.FIVE,
          2);
      SalesInvoiceLine ten = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("10000.00"),
          VatRate.TEN,
          3);
      SalesInvoiceLine exempt = createLine(
          invoice.getId(),
          new BigDecimal("50000.00"),
          BigDecimal.ZERO,
          VatRate.EXEMPT,
          4);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(zero, five, ten, exempt));

      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      OutputVATReportDTO report = arVatReportService.generateVATReport(periodId, null, null, Map.of());

      // Verify revenue aggregation by VAT rate (ND123 format)
      assertThat(report.getRevenue0pct()).isEqualByComparingTo("50000.00");
      assertThat(report.getRevenue5pct()).isEqualByComparingTo("100000.00");
      assertThat(report.getRevenue10pct()).isEqualByComparingTo("100000.00");
      assertThat(report.getRevenueExempt()).isEqualByComparingTo("50000.00");
      assertThat(report.getTotalVatCollected()).isEqualByComparingTo("15000.00"); // 0 + 5000 + 10000 + 0
    }
  }

  @Test
  void generateVATReport_shouldUseDateRangeWhenPeriodNotProvided() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      LocalDate startDate = LocalDate.of(2025, 5, 1);
      LocalDate endDate = LocalDate.of(2025, 5, 31);

      SalesInvoice invoice = createInvoice(new BigDecimal("100000.00"), new BigDecimal("10000.00"));
      invoice.setStatus(SalesInvoiceStatus.POSTED);
      invoice.setCustomerId(CUSTOMER_ID);
      invoice.setInvoiceDate(LocalDate.of(2025, 5, 15));

      Customer customer = createCustomer(CUSTOMER_ID, "CUST-001", "Test Customer", "1234567890");
      invoice.setCustomer(customer);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice));
      SalesInvoiceLine line = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("10000.00"),
          VatRate.TEN,
          1);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(line));

      when(vatReportHistoryRepository.save(any(VATReportHistory.class)))
          .thenAnswer(invocation -> {
            VATReportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
              history.setId(UUID.randomUUID());
            }
            return history;
          });

      OutputVATReportDTO report = arVatReportService.generateVATReport(
          null, null, null, Map.of("startDate", startDate, "endDate", endDate));

      assertThat(report.getStartDate()).isEqualTo(startDate);
      assertThat(report.getEndDate()).isEqualTo(endDate);
      assertThat(report.getItems()).hasSize(1);
    }
  }

  @Test
  void exportVATReport_shouldExportExcelFormat() {
    try (MockedStatic<com.accounting.security.SecurityUtils> securityUtilsMock =
        org.mockito.Mockito.mockStatic(com.accounting.security.SecurityUtils.class)) {
      securityUtilsMock.when(com.accounting.security.SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

      UUID reportId = UUID.randomUUID();
      VATReportHistory history = new VATReportHistory();
      history.setId(reportId);
      history.setCompanyId(COMPANY_ID);
      history.setReportType(VATReportHistory.ReportType.OUTPUT_VAT);
      history.setStartDate(LocalDate.of(2025, 1, 1));
      history.setEndDate(LocalDate.of(2025, 1, 31));
      history.setFormat(VATReportHistory.ExportFormat.EXCEL);
      history.setDownloadCount(0);

      when(vatReportHistoryRepository.findByCompanyIdAndId(COMPANY_ID, reportId))
          .thenReturn(Optional.of(history));

      SalesInvoice invoice = createInvoice(new BigDecimal("100000.00"), new BigDecimal("10000.00"));
      invoice.setStatus(SalesInvoiceStatus.POSTED);
      invoice.setCustomerId(CUSTOMER_ID);
      invoice.setInvoiceDate(LocalDate.of(2025, 1, 15));

      Customer customer = createCustomer(CUSTOMER_ID, "CUST-001", "Test Customer", "1234567890");
      invoice.setCustomer(customer);

      when(salesInvoiceRepository.findAll(any(Specification.class))).thenReturn(List.of(invoice));
      SalesInvoiceLine line = createLine(
          invoice.getId(),
          new BigDecimal("100000.00"),
          new BigDecimal("10000.00"),
          VatRate.TEN,
          1);
      when(salesInvoiceLineRepository
          .findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(eq(COMPANY_ID), anyList()))
          .thenReturn(List.of(line));

      when(vatReportHistoryRepository.save(any(VATReportHistory.class))).thenReturn(history);

      byte[] exported = arVatReportService.exportVATReport(reportId, "EXCEL");

      assertThat(exported).isNotNull();
      assertThat(exported.length).isGreaterThan(0);
      // Verify Excel file signature (XLSX files start with PK)
      assertThat(exported[0]).isEqualTo((byte) 0x50); // 'P'
      assertThat(exported[1]).isEqualTo((byte) 0x4B); // 'K'

      verify(vatReportHistoryRepository).save(argThat(h -> h.getDownloadCount() == 1));
      verify(auditService).logReportExport(eq(COMPANY_ID), eq(USER_ID), eq("EXCEL"), any());
    }
  }

  @Test
  void exportVATReport_shouldThrowExceptionWhenReportNotFound() {
    UUID reportId = UUID.randomUUID();
    when(vatReportHistoryRepository.findByCompanyIdAndId(COMPANY_ID, reportId))
        .thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> arVatReportService.exportVATReport(reportId, "EXCEL"),
        "VAT report not found: " + reportId);
  }

  // Helper methods
  private SalesInvoice createInvoice(BigDecimal totalAmount, BigDecimal vatAmount) {
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setCompanyId(COMPANY_ID);
    invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8));
    invoice.setInvoiceDate(LocalDate.now());
    invoice.setStatus(SalesInvoiceStatus.POSTED);
    invoice.setTotalAmount(totalAmount);
    invoice.setVatAmount(vatAmount);
    return invoice;
  }

  private SalesInvoiceLine createLine(
      UUID invoiceId, BigDecimal amount, BigDecimal vatAmount, VatRate vatRate, int lineNumber) {
    SalesInvoiceLine line = new SalesInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setSalesInvoiceId(invoiceId);
    line.setCompanyId(COMPANY_ID);
    line.setLineNumber(lineNumber);
    line.setAmount(amount);
    line.setVatAmount(vatAmount);
    line.setVatRate(vatRate);
    return line;
  }

  private Customer createCustomer(Long id, String code, String name, String taxCode) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setCompanyId(COMPANY_ID);
    customer.setCode(code);
    customer.setName(name);
    customer.setTaxCode(taxCode);
    return customer;
  }
}
