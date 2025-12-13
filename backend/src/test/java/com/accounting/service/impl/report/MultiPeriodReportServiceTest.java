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
import com.accounting.dto.report.ComparisonSettingsDTO;
import com.accounting.dto.report.MultiPeriodReportDTO;
import com.accounting.dto.report.VarianceDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.CompanySettings;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.PeriodManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiPeriodReportServiceTest {

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

  @Mock
  private CompanySettingsRepository companySettingsRepository;

  private StatutoryReportServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;
  private ObjectMapper objectMapper;

  private static final Long COMPANY_ID = 1L;
  private static final Long OTHER_COMPANY_ID = 2L;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

    service = new StatutoryReportServiceImpl(
        reportMappingRepository,
        voucherLineRepository,
        chartOfAccountsRepository,
        periodManagementService,
        companyService,
        companySettingsRepository,
        objectMapper);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
  }

  private AccountingPeriodDTO createPeriod(UUID id, String name, int year, int month, PeriodStatus status) {
    AccountingPeriodDTO period = new AccountingPeriodDTO();
    period.setId(id);
    period.setPeriodName(name);
    period.setStartDate(LocalDate.of(year, month, 1));
    period.setEndDate(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    period.setStatus(status);
    period.setFiscalYear(year);
    period.setCompanyId(COMPANY_ID);
    return period;
  }

  private Company createCompany() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    company.setName("Test Company");
    company.setTaxCode("0123456789");
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

  private List<Object[]> createBalances(Object[]... items) {
    List<Object[]> result = new ArrayList<>();
    for (Object[] item : items) {
      result.add(item);
    }
    return result;
  }

  private void setupDefaultMocks(List<AccountingPeriodDTO> periods, List<ReportMapping> mappings,
      List<ChartOfAccount> accounts, List<List<Object[]>> periodBalances) {
    Company company = createCompany();
    when(companyService.getCurrentCompanySettings()).thenReturn(company);
    when(reportMappingRepository.findCurrentByCompanyAndReportType(eq(COMPANY_ID), anyString()))
        .thenReturn(mappings);
    when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);

    for (int i = 0; i < periods.size(); i++) {
      AccountingPeriodDTO period = periods.get(i);
      when(periodManagementService.getPeriodById(period.getId())).thenReturn(Optional.of(period));
      if (i < periodBalances.size()) {
        when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID),
            eq(period.getEndDate().plusDays(1))))
            .thenReturn(periodBalances.get(i));
      }
    }

    CompanySettings settings = new CompanySettings();
    settings.setCompanyId(COMPANY_ID);
    when(companySettingsRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(settings));
  }

  @Nested
  @DisplayName("Multi-Period Generation Tests")
  class MultiPeriodGenerationTests {

    @Test
    @DisplayName("Should generate report with 2 periods")
    void shouldGenerateReportWith2Periods() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("120000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      assertNotNull(result);
      assertEquals("B01", result.reportType());
      assertEquals(2, result.periods().size());
      assertEquals(1, result.lines().size());
      assertFalse(result.hasDraftPeriod());
    }

    @Test
    @DisplayName("Should generate report with 3 periods")
    void shouldGenerateReportWith3Periods() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();
      UUID period3Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Q1 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Q2 2024", 2024, 4, PeriodStatus.CLOSED),
          createPeriod(period3Id, "Q3 2024", 2024, 7, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("120000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("150000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id, period3Id));

      assertNotNull(result);
      assertEquals(3, result.periods().size());
      assertEquals(2, result.lines().get(0).variances().size());
    }

    @Test
    @DisplayName("Should generate report with 4 periods")
    void shouldGenerateReportWith4Periods() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();
      UUID period3Id = UUID.randomUUID();
      UUID period4Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Q1 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Q2 2024", 2024, 4, PeriodStatus.CLOSED),
          createPeriod(period3Id, "Q3 2024", 2024, 7, PeriodStatus.CLOSED),
          createPeriod(period4Id, "Q4 2024", 2024, 10, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("120000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("150000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("180000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id, period3Id, period4Id));

      assertNotNull(result);
      assertEquals(4, result.periods().size());
      assertEquals(3, result.lines().get(0).variances().size());
    }

    @Test
    @DisplayName("Should mark report as having draft period when any period is open")
    void shouldMarkReportAsDraftWhenAnyPeriodIsOpen() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.OPEN));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      setupDefaultMocks(periods, mappings, List.of(), List.of(new ArrayList<>(), new ArrayList<>()));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      assertTrue(result.hasDraftPeriod());
    }
  }

  @Nested
  @DisplayName("Variance Calculation Edge Cases")
  class VarianceCalculationEdgeCasesTests {

    @Test
    @DisplayName("Should calculate normal variance correctly (100k → 120k = 20k, +20%)")
    void shouldCalculateNormalVarianceCorrectly() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("120000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      assertEquals(1, line.variances().size());

      VarianceDTO variance = line.variances().get(0);
      assertEquals(new BigDecimal("20000"), variance.absoluteVariance());
      assertEquals(20.0, variance.percentVariance(), 0.01);
    }

    @Test
    @DisplayName("Should handle zero prior value (0 → 50k should show infinity)")
    void shouldHandleZeroPriorValue() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, BigDecimal.ZERO, BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("50000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals(new BigDecimal("50000"), variance.absoluteVariance());
      assertTrue(Double.isInfinite(variance.percentVariance()));
    }

    @Test
    @DisplayName("Should handle zero current value (50k → 0 = -50k, -100%)")
    void shouldHandleZeroCurrentValue() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("50000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, BigDecimal.ZERO, BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals(new BigDecimal("-50000"), variance.absoluteVariance());
      assertEquals(-100.0, variance.percentVariance(), 0.01);
    }

    @Test
    @DisplayName("Should handle both zero values (0 → 0 = neutral)")
    void shouldHandleBothZeroValues() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      setupDefaultMocks(periods, mappings, List.of(), List.of(new ArrayList<>(), new ArrayList<>()));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals(BigDecimal.ZERO, variance.absoluteVariance());
      assertNull(variance.percentVariance());
      assertEquals("NEUTRAL", variance.direction());
    }
  }

  @Nested
  @DisplayName("Materiality Threshold Tests")
  class MaterialityThresholdTests {

    private void setupWithThresholds(double percentThreshold, BigDecimal absoluteThreshold,
        List<AccountingPeriodDTO> periods, List<ReportMapping> mappings,
        List<ChartOfAccount> accounts, List<List<Object[]>> balances) throws Exception {

      Company company = createCompany();
      when(companyService.getCurrentCompanySettings()).thenReturn(company);
      when(reportMappingRepository.findCurrentByCompanyAndReportType(eq(COMPANY_ID), anyString()))
          .thenReturn(mappings);
      when(chartOfAccountsRepository.findByCompanyId(COMPANY_ID)).thenReturn(accounts);

      for (int i = 0; i < periods.size(); i++) {
        AccountingPeriodDTO period = periods.get(i);
        when(periodManagementService.getPeriodById(period.getId())).thenReturn(Optional.of(period));
        if (i < balances.size()) {
          when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID),
              eq(period.getEndDate().plusDays(1))))
              .thenReturn(balances.get(i));
        }
      }

      CompanySettings settings = new CompanySettings();
      settings.setCompanyId(COMPANY_ID);
      ComparisonSettingsDTO settingsDto = new ComparisonSettingsDTO(
          percentThreshold, absoluteThreshold, "YOY", true, false);
      settings.setComparisonSettings(objectMapper.writeValueAsString(settingsDto));
      when(companySettingsRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(settings));
    }

    @Test
    @DisplayName("Should not flag variance below percent threshold (5% when threshold is 10%)")
    void shouldNotFlagVarianceBelowPercentThreshold() throws Exception {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("105000"), BigDecimal.ZERO }));

      setupWithThresholds(10.0, new BigDecimal("1000000"), periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      assertFalse(line.isMaterial());
    }

    @Test
    @DisplayName("Should flag variance above percent threshold (15% when threshold is 10%)")
    void shouldFlagVarianceAbovePercentThreshold() throws Exception {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("115000"), BigDecimal.ZERO }));

      setupWithThresholds(10.0, new BigDecimal("100000000"), periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      assertTrue(line.isMaterial());
    }

    @Test
    @DisplayName("Should flag variance above absolute threshold (2M when threshold is 1M)")
    void shouldFlagVarianceAboveAbsoluteThreshold() throws Exception {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("102000000"), BigDecimal.ZERO }));

      setupWithThresholds(100.0, new BigDecimal("1000000"), periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      assertTrue(line.isMaterial());
    }
  }

  @Nested
  @DisplayName("Variance Direction Awareness Tests")
  class VarianceDirectionAwarenessTests {

    @Test
    @DisplayName("B02 expense line decrease should be FAVORABLE")
    void b02ExpenseLineDecreaseShouldBeFavorable() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("632", "Cost of Goods Sold", "632*", false, null, 1, 1));
      mappings.forEach(m -> m.setReportType("B02"));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "6321", "COGS", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("500000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("400000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(), any()))
          .thenReturn(balances.get(0))
          .thenReturn(balances.get(1));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B02",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("FAVORABLE", variance.direction());
    }

    @Test
    @DisplayName("B02 expense line increase should be UNFAVORABLE")
    void b02ExpenseLineIncreaseShouldBeUnfavorable() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("632", "Cost of Goods Sold", "632*", false, null, 1, 1));
      mappings.forEach(m -> m.setReportType("B02"));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "6321", "COGS", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("400000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("500000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(), any()))
          .thenReturn(balances.get(0))
          .thenReturn(balances.get(1));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B02",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("UNFAVORABLE", variance.direction());
    }

    @Test
    @DisplayName("B02 revenue line increase should be FAVORABLE")
    void b02RevenueLineIncreaseShouldBeFavorable() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Revenue", "511*", false, null, 1, 1));
      mappings.forEach(m -> m.setReportType("B02"));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Revenue", "CREDIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("1000000") }),
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("1200000") }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(), any()))
          .thenReturn(balances.get(0))
          .thenReturn(balances.get(1));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B02",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("FAVORABLE", variance.direction());
    }

    @Test
    @DisplayName("B02 revenue line decrease should be UNFAVORABLE")
    void b02RevenueLineDecreaseShouldBeUnfavorable() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("01", "Revenue", "511*", false, null, 1, 1));
      mappings.forEach(m -> m.setReportType("B02"));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "5111", "Revenue", "CREDIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("1200000") }),
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("1000000") }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      when(voucherLineRepository.calculatePeriodActivity(eq(COMPANY_ID), any(), any()))
          .thenReturn(balances.get(0))
          .thenReturn(balances.get(1));

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B02",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("UNFAVORABLE", variance.direction());
    }

    @Test
    @DisplayName("B01 asset line increase should be FAVORABLE")
    void b01AssetLineIncreaseShouldBeFavorable() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("150000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("FAVORABLE", variance.direction());
    }

    @Test
    @DisplayName("B01 liability line (code 3xx) should be NEUTRAL")
    void b01LiabilityLineShouldBeNeutral() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("310", "Accounts Payable", "331*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "3311", "Accounts Payable", "CREDIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("100000") }),
          createBalances(new Object[] { 1L, BigDecimal.ZERO, new BigDecimal("150000") }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      VarianceDTO variance = line.variances().get(0);
      assertEquals("NEUTRAL", variance.direction());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should throw exception when more than 4 periods requested")
    void shouldThrowExceptionWhenMoreThan4PeriodsRequested() {
      List<UUID> periodIds = List.of(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          UUID.randomUUID(), UUID.randomUUID());

      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", periodIds));
    }

    @Test
    @DisplayName("Should throw exception when no periods provided")
    void shouldThrowExceptionWhenNoPeriodsProvided() {
      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", List.of()));
    }

    @Test
    @DisplayName("Should throw exception when null periods provided")
    void shouldThrowExceptionWhenNullPeriodsProvided() {
      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", null));
    }

    @Test
    @DisplayName("Should throw exception when periods from different companies")
    void shouldThrowExceptionWhenPeriodsFromDifferentCompanies() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      AccountingPeriodDTO period1 = createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED);
      AccountingPeriodDTO period2 = createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED);
      period2.setCompanyId(OTHER_COMPANY_ID);

      when(periodManagementService.getPeriodById(period1Id)).thenReturn(Optional.of(period1));
      when(periodManagementService.getPeriodById(period2Id)).thenReturn(Optional.of(period2));

      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", List.of(period1Id, period2Id)));
    }

    @Test
    @DisplayName("Should throw exception for invalid report type")
    void shouldThrowExceptionForInvalidReportType() {
      UUID periodId = UUID.randomUUID();
      AccountingPeriodDTO period = createPeriod(periodId, "Jan 2024", 2024, 1, PeriodStatus.CLOSED);
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.of(period));

      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("INVALID", List.of(periodId)));
    }

    @Test
    @DisplayName("Should throw exception when period not found")
    void shouldThrowExceptionWhenPeriodNotFound() {
      UUID periodId = UUID.randomUUID();
      when(periodManagementService.getPeriodById(periodId)).thenReturn(Optional.empty());

      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", List.of(periodId)));
    }

    @Test
    @DisplayName("Should throw exception when company context missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);

      assertThrows(ResponseStatusException.class, () ->
          service.generateMultiPeriodReport("B01", List.of(UUID.randomUUID())));
    }
  }

  @Nested
  @DisplayName("Sparkline Data Generation Tests")
  class SparklineDataGenerationTests {

    @Test
    @DisplayName("Should generate normalized sparkline data")
    void shouldGenerateNormalizedSparklineData() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();
      UUID period3Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED),
          createPeriod(period3Id, "Mar 2024", 2024, 3, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("150000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("200000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id, period3Id));

      var line = result.lines().get(0);
      assertNotNull(line.sparklineData());
      assertEquals(3, line.sparklineData().size());
      assertEquals(0.0, line.sparklineData().get(0), 0.01);
      assertEquals(0.5, line.sparklineData().get(1), 0.01);
      assertEquals(1.0, line.sparklineData().get(2), 0.01);
    }

    @Test
    @DisplayName("Should handle all same values in sparkline (returns 0.5 for all)")
    void shouldHandleAllSameValuesInSparkline() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();

      List<AccountingPeriodDTO> periods = List.of(
          createPeriod(period1Id, "Jan 2024", 2024, 1, PeriodStatus.CLOSED),
          createPeriod(period2Id, "Feb 2024", 2024, 2, PeriodStatus.CLOSED));

      List<ReportMapping> mappings = List.of(
          createMapping("110", "I. Cash", "111*", false, null, 1, 1));

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash", "DEBIT"));

      List<List<Object[]>> balances = List.of(
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }),
          createBalances(new Object[] { 1L, new BigDecimal("100000"), BigDecimal.ZERO }));

      setupDefaultMocks(periods, mappings, accounts, balances);

      MultiPeriodReportDTO result = service.generateMultiPeriodReport("B01",
          List.of(period1Id, period2Id));

      var line = result.lines().get(0);
      assertNotNull(line.sparklineData());
      assertEquals(2, line.sparklineData().size());
      assertEquals(0.5, line.sparklineData().get(0), 0.01);
      assertEquals(0.5, line.sparklineData().get(1), 0.01);
    }
  }
}
