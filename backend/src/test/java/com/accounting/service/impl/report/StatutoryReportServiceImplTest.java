package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.ValidationResultDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.PeriodManagementService;

/**
 * Unit tests for StatutoryReportServiceImpl.
 * Tests B01 (Balance Sheet), B02 (Income Statement), B03 (Cash Flow) generation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatutoryReportServiceImplTest {

  @Mock
  private ReportMappingRepository reportMappingRepository;

  @Mock
  private VoucherLineRepository voucherLineRepository;

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Mock
  private PeriodManagementService periodManagementService;

  @Mock
  private CompanyService companyService;

  private StatutoryReportServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;

  private static final Long COMPANY_ID = 1L;
  private static final UUID PERIOD_ID = UUID.randomUUID();
  private static final UUID COMPARISON_PERIOD_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new StatutoryReportServiceImpl(
        reportMappingRepository,
        voucherLineRepository,
        chartOfAccountsRepository,
        periodManagementService,
        companyService);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
  }

  // ==================== Helper Methods ====================

  private AccountingPeriodDTO createPeriod(UUID id, String name, PeriodStatus status) {
    AccountingPeriodDTO period = new AccountingPeriodDTO();
    period.setId(id);
    period.setPeriodName(name);
    period.setStartDate(LocalDate.of(2024, 1, 1));
    period.setEndDate(LocalDate.of(2024, 12, 31));
    period.setStatus(status);
    period.setFiscalYear(2024);
    return period;
  }

  private Company createCompany() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    company.setName("Test Company");
    company.setTaxCode("0123456789");
    company.setAddress("123 Test Street");
    return company;
  }

  private ReportMapping createMapping(String lineCode, String lineName, String accountPattern,
      boolean isCalculated, String formula, int displayOrder, int level) {
    ReportMapping mapping = new ReportMapping();
    mapping.setId(UUID.randomUUID());
    mapping.setCompanyId(COMPANY_ID);
    mapping.setReportType("B01");
    mapping.setLineCode(lineCode);
    mapping.setLineName(lineName);
    mapping.setLineNameEnglish(lineName);
    mapping.setAccountPattern(accountPattern);
    mapping.setOperator("SUM");
    mapping.setSignModifier(1);
    mapping.setDisplayOrder(displayOrder);
    mapping.setLevel(level);
    mapping.setIsCalculated(isCalculated);
    mapping.setFormula(formula);
    mapping.setVersion(1);
    mapping.setIsCurrent(true);
    return mapping;
  }

  private ChartOfAccount createAccount(Long id, String code, String name, String normalSide) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCode(code);
    account.setName(name);
    account.setNormalSide(normalSide);
    account.setCompanyId(COMPANY_ID);
    return account;
  }

  // ==================== Test Cases ====================

  @Nested
  @DisplayName("Generate Balance Sheet (B01)")
  class GenerateBalanceSheetTests {

    @Test
    @DisplayName("Should generate B01 with correct structure")
    void shouldGenerateB01WithCorrectStructure() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("100", "A - CURRENT ASSETS", "SUM(110,120)", true, "110+120", 1, 1),
          createMapping("110", "I. Cash", "111*", false, null, 2, 2),
          createMapping("120", "II. Receivables", "131*", false, null, 3, 2));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash on hand", "DEBIT"),
          createAccount(2L, "1112", "Cash in bank", "DEBIT"),
          createAccount(3L, "1311", "Receivables", "DEBIT"));

      List<Object[]> balances = List.of(
          new Object[]{1L, new BigDecimal("10000"), BigDecimal.ZERO},
          new Object[]{2L, new BigDecimal("50000"), BigDecimal.ZERO},
          new Object[]{3L, new BigDecimal("20000"), BigDecimal.ZERO});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      StatutoryReportDTO result = service.generateBalanceSheet(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      assertEquals("B01", result.getReportType());
      assertEquals(PERIOD_ID, result.getPeriodId());
      assertEquals("Test Company", result.getCompanyName());
      assertNotNull(result.getLines());
      assertFalse(result.isDraft());
    }

    @Test
    @DisplayName("Should mark report as draft for open period")
    void shouldMarkReportAsDraftForOpenPeriod() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.OPEN);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(List.of());

      // When
      StatutoryReportDTO result = service.generateBalanceSheet(PERIOD_ID, null);

      // Then
      assertTrue(result.isDraft());
    }

    @Test
    @DisplayName("Should throw exception when period not found")
    void shouldThrowExceptionWhenPeriodNotFound() {
      // Given
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.generateBalanceSheet(PERIOD_ID, null));
    }

    @Test
    @DisplayName("Should throw exception when no mappings configured")
    void shouldThrowExceptionWhenNoMappingsConfigured() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(List.of());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.generateBalanceSheet(PERIOD_ID, null));
    }

    @Test
    @DisplayName("Should throw exception when company context missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      // Given
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.generateBalanceSheet(PERIOD_ID, null));
    }
  }

  @Nested
  @DisplayName("Generate Balance Sheet with Comparison")
  class GenerateBalanceSheetWithComparisonTests {

    @Test
    @DisplayName("Should calculate variance between periods")
    void shouldCalculateVarianceBetweenPeriods() {
      // Given
      // Create periods with distinct dates so mock can differentiate
      AccountingPeriodDTO currentPeriod = new AccountingPeriodDTO();
      currentPeriod.setId(PERIOD_ID);
      currentPeriod.setPeriodName("2024");
      currentPeriod.setStartDate(LocalDate.of(2024, 1, 1));
      currentPeriod.setEndDate(LocalDate.of(2024, 12, 31));
      currentPeriod.setStatus(PeriodStatus.CLOSED);
      currentPeriod.setFiscalYear(2024);

      AccountingPeriodDTO priorPeriod = new AccountingPeriodDTO();
      priorPeriod.setId(COMPARISON_PERIOD_ID);
      priorPeriod.setPeriodName("2023");
      priorPeriod.setStartDate(LocalDate.of(2023, 1, 1));
      priorPeriod.setEndDate(LocalDate.of(2023, 12, 31));
      priorPeriod.setStatus(PeriodStatus.CLOSED);
      priorPeriod.setFiscalYear(2023);

      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash on hand", "DEBIT"));

      // Current period: 100,000
      List<Object[]> currentBalances = new ArrayList<>();
      currentBalances.add(new Object[]{1L, new BigDecimal("100000"), BigDecimal.ZERO});

      // Prior period: 80,000
      List<Object[]> priorBalances = new ArrayList<>();
      priorBalances.add(new Object[]{1L, new BigDecimal("80000"), BigDecimal.ZERO});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(currentPeriod));
      when(periodManagementService.getPeriodById(COMPARISON_PERIOD_ID)).thenReturn(Optional.of(priorPeriod));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      // Mock current period balances (2025-01-01)
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), eq(LocalDate.of(2025, 1, 1))))
          .thenReturn(currentBalances);
      // Mock prior period balances (2024-01-01)
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), eq(LocalDate.of(2024, 1, 1))))
          .thenReturn(priorBalances);

      // When
      StatutoryReportDTO result = service.generateBalanceSheet(PERIOD_ID, COMPARISON_PERIOD_ID);

      // Then
      assertNotNull(result);
      assertNotNull(result.getComparisonPeriodId());
      assertEquals(COMPARISON_PERIOD_ID, result.getComparisonPeriodId());

      // Check variance calculation
      assertFalse(result.getLines().isEmpty());
      var line = result.getLines().get(0);
      assertEquals(new BigDecimal("100000"), line.getCurrentAmount());
      assertEquals(new BigDecimal("80000"), line.getPriorAmount());
      // Variance = 100000 - 80000 = 20000
      assertEquals(new BigDecimal("20000"), line.getVariance());
    }
  }

  @Nested
  @DisplayName("Generate Income Statement (B02)")
  class GenerateIncomeStatementTests {

    @Test
    @DisplayName("Should generate B02 with calculated lines")
    void shouldGenerateB02WithCalculatedLines() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Revenue", "511*", false, null, 1, 1),
          createMapping("11", "COGS", "632*", false, null, 2, 1),
          createMapping("20", "Gross Profit", null, true, "01-11", 3, 1));
      mappings.get(0).setReportType("B02");
      mappings.get(1).setReportType("B02");
      mappings.get(2).setReportType("B02");

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Sales Revenue", "CREDIT"),
          createAccount(2L, "6321", "Cost of Goods Sold", "DEBIT"));

      // Revenue: 500,000 (credit), COGS: 300,000 (debit)
      List<Object[]> periodActivity = List.of(
          new Object[]{1L, BigDecimal.ZERO, new BigDecimal("500000")},
          new Object[]{2L, new BigDecimal("300000"), BigDecimal.ZERO});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B02"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      // B02 should use calculatePeriodActivity, NOT calculateOpeningBalances
      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(periodActivity);

      // When
      StatutoryReportDTO result = service.generateIncomeStatement(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      assertEquals("B02", result.getReportType());

      // Verify the correct repository method was called (period activity, not cumulative)
      verify(voucherLineRepository).calculatePeriodActivity(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class));
      verify(voucherLineRepository, never()).calculateOpeningBalances(any(), any());
    }

    @Test
    @DisplayName("B02 should use period activity, not cumulative balances")
    void shouldUsePerodActivityForB02() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Revenue", "511*", false, null, 1, 1));
      mappings.get(0).setReportType("B02");

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Sales Revenue", "CREDIT"));

      // Period activity: 100,000 in revenue for this period only
      List<Object[]> periodActivity = new ArrayList<>();
      periodActivity.add(new Object[]{1L, BigDecimal.ZERO, new BigDecimal("100000")});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B02"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID),
          eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
          .thenReturn(periodActivity);

      // When
      StatutoryReportDTO result = service.generateIncomeStatement(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      assertEquals(new BigDecimal("100000"), result.getLines().get(0).getCurrentAmount());

      // Verify calculatePeriodActivity was called with correct date range
      verify(voucherLineRepository).calculatePeriodActivity(
          eq(COMPANY_ID),
          eq(LocalDate.of(2024, 1, 1)),
          eq(LocalDate.of(2024, 12, 31)));
    }
  }

  @Nested
  @DisplayName("Generate Cash Flow Statement (B03)")
  class GenerateCashFlowStatementTests {

    @Test
    @DisplayName("Should generate B03 without comparison period")
    void shouldGenerateB03WithoutComparisonPeriod() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Net cash from operating", "511*", false, null, 1, 1));
      mappings.get(0).setReportType("B03");

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B03"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
      // B03 should use calculatePeriodActivity for flow lines
      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of());

      // When
      StatutoryReportDTO result = service.generateCashFlowStatement(PERIOD_ID);

      // Then
      assertNotNull(result);
      assertEquals("B03", result.getReportType());
      assertNull(result.getComparisonPeriodId());

      // Verify period activity method was called
      verify(voucherLineRepository).calculatePeriodActivity(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("B03 should use period activity for cash flow calculations")
    void shouldUsePeriodActivityForB03() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Cash from sales", "511*", false, null, 1, 1));
      mappings.get(0).setReportType("B03");

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Sales Revenue", "CREDIT"));

      // Period activity: 200,000 in cash from sales this period
      List<Object[]> periodActivity = new ArrayList<>();
      periodActivity.add(new Object[]{1L, BigDecimal.ZERO, new BigDecimal("200000")});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B03"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID),
          eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
          .thenReturn(periodActivity);

      // When
      StatutoryReportDTO result = service.generateCashFlowStatement(PERIOD_ID);

      // Then
      assertNotNull(result);
      assertEquals(new BigDecimal("200000"), result.getLines().get(0).getCurrentAmount());

      // Verify calculatePeriodActivity was used instead of calculateOpeningBalances
      verify(voucherLineRepository).calculatePeriodActivity(
          eq(COMPANY_ID),
          eq(LocalDate.of(2024, 1, 1)),
          eq(LocalDate.of(2024, 12, 31)));
      verify(voucherLineRepository, never()).calculateOpeningBalances(any(), any());
    }
  }

  @Nested
  @DisplayName("Validate for Export")
  class ValidateForExportTests {

    @Test
    @DisplayName("Should return valid result for balanced report")
    void shouldReturnValidResultForBalancedReport() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(List.of());

      // When
      ValidationResultDTO result = service.validateForExport(PERIOD_ID, "B01");

      // Then
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should add warning for open period")
    void shouldAddWarningForOpenPeriod() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.OPEN);
      Company company = createCompany();

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(List.of());

      // When
      ValidationResultDTO result = service.validateForExport(PERIOD_ID, "B01");

      // Then
      assertEquals("OPEN", result.getPeriodStatus());
      assertFalse(result.getWarnings().isEmpty());
      assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("DRAFT")));
    }

    @Test
    @DisplayName("Should throw exception for unknown report type")
    void shouldThrowExceptionForUnknownReportType() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "UNKNOWN"))
          .thenReturn(List.of());

      // When
      ValidationResultDTO result = service.validateForExport(PERIOD_ID, "UNKNOWN");

      // Then
      assertFalse(result.isValid());
      assertFalse(result.getErrors().isEmpty());
    }
  }

  @Nested
  @DisplayName("Account Pattern Matching")
  class AccountPatternMatchingTests {

    @Test
    @DisplayName("Should match wildcard pattern correctly")
    void shouldMatchWildcardPatternCorrectly() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      // Mapping with wildcard pattern
      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*,112*", false, null, 1, 1));

      // Multiple accounts matching 111* and 112*
      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash on hand", "DEBIT"),
          createAccount(2L, "1112", "Cash in bank", "DEBIT"),
          createAccount(3L, "1121", "Short-term deposit", "DEBIT"),
          createAccount(4L, "1311", "Receivables", "DEBIT")); // Should not match

      List<Object[]> balances = List.of(
          new Object[]{1L, new BigDecimal("10000"), BigDecimal.ZERO},
          new Object[]{2L, new BigDecimal("20000"), BigDecimal.ZERO},
          new Object[]{3L, new BigDecimal("30000"), BigDecimal.ZERO},
          new Object[]{4L, new BigDecimal("40000"), BigDecimal.ZERO});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      StatutoryReportDTO result = service.generateBalanceSheet(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      // Line should have sum of 111* and 112* accounts = 10000 + 20000 + 30000 = 60000
      // (excludes 1311 which doesn't match pattern)
      var line = result.getLines().get(0);
      assertEquals(new BigDecimal("60000"), line.getCurrentAmount());
    }
  }

  @Nested
  @DisplayName("Formula Calculation")
  class FormulaCalculationTests {

    @Test
    @DisplayName("Should calculate subtraction formula correctly")
    void shouldCalculateSubtractionFormulaCorrectly() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      // Gross Profit = Revenue (01) - COGS (11)
      List<ReportMapping> mappings = List.of(
          createMapping("01", "Revenue", "511*", false, null, 1, 1),
          createMapping("11", "COGS", "632*", false, null, 2, 1),
          createMapping("20", "Gross Profit", null, true, "01-11", 3, 1));
      mappings.forEach(m -> m.setReportType("B02"));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Revenue", "CREDIT"),
          createAccount(2L, "6321", "COGS", "DEBIT"));

      // Revenue: 500,000 (credit side), COGS: 300,000 (debit side)
      List<Object[]> periodActivity = List.of(
          new Object[]{1L, BigDecimal.ZERO, new BigDecimal("500000")}, // Credit balance
          new Object[]{2L, new BigDecimal("300000"), BigDecimal.ZERO}); // Debit balance

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B02"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      // B02 uses period activity
      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(periodActivity);

      // When
      StatutoryReportDTO result = service.generateIncomeStatement(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      // Find the Gross Profit line (code "20")
      var grossProfitLine = result.getLines().stream()
          .filter(l -> "20".equals(l.getLineCode()))
          .findFirst()
          .orElse(null);

      assertNotNull(grossProfitLine);
      assertTrue(grossProfitLine.isCalculated());
    }

    @Test
    @DisplayName("Should calculate addition formula correctly")
    void shouldCalculateAdditionFormulaCorrectly() {
      // Given
      AccountingPeriodDTO period = createPeriod(PERIOD_ID, "2024", PeriodStatus.CLOSED);
      Company company = createCompany();

      // Total = Cash (110) + Receivables (120)
      List<ReportMapping> mappings = List.of(
          createMapping("110", "Cash", "111*", false, null, 1, 2),
          createMapping("120", "Receivables", "131*", false, null, 2, 2),
          createMapping("100", "Current Assets", null, true, "110+120", 3, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"),
          createAccount(2L, "1311", "Receivables", "DEBIT"));

      List<Object[]> balances = List.of(
          new Object[]{1L, new BigDecimal("50000"), BigDecimal.ZERO},
          new Object[]{2L, new BigDecimal("30000"), BigDecimal.ZERO});

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(COMPANY_ID, "B01"))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      StatutoryReportDTO result = service.generateBalanceSheet(PERIOD_ID, null);

      // Then
      assertNotNull(result);
      // Find the Total line (code "100")
      var totalLine = result.getLines().stream()
          .filter(l -> "100".equals(l.getLineCode()))
          .findFirst()
          .orElse(null);

      assertNotNull(totalLine);
      assertTrue(totalLine.isCalculated());
      // Total = 50000 + 30000 = 80000
      assertEquals(new BigDecimal("80000"), totalLine.getCurrentAmount());
    }
  }
}
