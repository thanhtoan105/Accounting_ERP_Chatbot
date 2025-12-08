package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.AccountContributionDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.DrillDownService.VoucherDetailDTO;
import com.accounting.service.DrillDownService.VoucherSummaryDTO;
import com.accounting.service.PeriodManagementService;

/**
 * Unit tests for DrillDownServiceImpl.
 * Tests drill-down from report lines to accounts to vouchers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrillDownServiceImplTest {

  @Mock
  private ReportMappingRepository reportMappingRepository;

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Mock
  private VoucherLineRepository voucherLineRepository;

  @Mock
  private VoucherRepository voucherRepository;

  @Mock
  private PeriodManagementService periodManagementService;

  private DrillDownServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;

  private static final Long COMPANY_ID = 1L;
  private static final UUID PERIOD_ID = UUID.randomUUID();
  private static final String REPORT_TYPE = "B01";
  private static final String LINE_CODE = "110";

  @BeforeEach
  void setUp() {
    service = new DrillDownServiceImpl(
        reportMappingRepository,
        chartOfAccountsRepository,
        voucherLineRepository,
        voucherRepository,
        periodManagementService);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
  }

  // ==================== Helper Methods ====================

  private AccountingPeriodDTO createPeriod() {
    AccountingPeriodDTO period = new AccountingPeriodDTO();
    period.setId(PERIOD_ID);
    period.setPeriodName("2024");
    period.setStartDate(LocalDate.of(2024, 1, 1));
    period.setEndDate(LocalDate.of(2024, 12, 31));
    period.setStatus(PeriodStatus.CLOSED);
    period.setFiscalYear(2024);
    return period;
  }

  private ReportMapping createMapping(String lineCode, String accountPattern, boolean isCalculated) {
    ReportMapping mapping = new ReportMapping();
    mapping.setId(UUID.randomUUID());
    mapping.setCompanyId(COMPANY_ID);
    mapping.setReportType(REPORT_TYPE);
    mapping.setLineCode(lineCode);
    mapping.setLineName("Test Line");
    mapping.setAccountPattern(accountPattern);
    mapping.setOperator("SUM");
    mapping.setSignModifier(1);
    mapping.setLevel(1);
    mapping.setIsCalculated(isCalculated);
    mapping.setVersion(1);
    mapping.setIsCurrent(true);
    return mapping;
  }

  private ChartOfAccount createAccount(Long id, String code, String name) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCode(code);
    account.setName(name);
    account.setNormalSide("DEBIT");
    account.setCompanyId(COMPANY_ID);
    return account;
  }

  private Voucher createVoucher(UUID id, String voucherNumber, LocalDate date, String status) {
    Voucher voucher = new Voucher();
    voucher.setId(id);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(date);
    voucher.setDescription("Test voucher");
    voucher.setStatus(status);
    voucher.setCompanyId(COMPANY_ID);
    voucher.setCreatedAt(Instant.now());
    return voucher;
  }

  private VoucherLine createVoucherLine(UUID voucherId, Long accountId, BigDecimal debit, BigDecimal credit) {
    VoucherLine line = new VoucherLine();
    line.setId(UUID.randomUUID());
    line.setVoucherId(voucherId);
    line.setAccountId(accountId);
    line.setDebit(debit);
    line.setCredit(credit);
    line.setLineNumber(1);
    line.setDescription("Test line");
    return line;
  }

  // ==================== Test Cases ====================

  @Nested
  @DisplayName("Get Accounts for Line")
  class GetAccountsForLineTests {

    @Test
    @DisplayName("Should return accounts matching wildcard pattern")
    void shouldReturnAccountsMatchingWildcardPattern() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "111*", false);
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash on hand"),
          createAccount(2L, "1112", "Cash in bank"),
          createAccount(3L, "1113", "Cash in transit"));

      List<Object[]> balances = new ArrayList<>();
      balances.add(new Object[]{1L, new BigDecimal("10000"), BigDecimal.ZERO});
      balances.add(new Object[]{2L, new BigDecimal("50000"), BigDecimal.ZERO});
      balances.add(new Object[]{3L, new BigDecimal("5000"), BigDecimal.ZERO});

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findByCompanyIdAndCodeStartingWith(COMPANY_ID, "111"))
          .thenReturn(accounts);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      Page<AccountContributionDTO> result = service.getAccountsForLine(
          REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(3, result.getTotalElements());

      // Verify sorting by account code
      List<AccountContributionDTO> content = result.getContent();
      assertEquals("1111", content.get(0).getAccountCode());
      assertEquals("1112", content.get(1).getAccountCode());
      assertEquals("1113", content.get(2).getAccountCode());
    }

    @Test
    @DisplayName("Should return accounts matching comma-separated patterns")
    void shouldReturnAccountsMatchingCommaSeparatedPatterns() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "111*,112*", false);
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      List<ChartOfAccount> accounts111 = List.of(
          createAccount(1L, "1111", "Cash on hand"));
      List<ChartOfAccount> accounts112 = List.of(
          createAccount(2L, "1121", "Short-term deposit"));

      List<Object[]> balances = new ArrayList<>();
      balances.add(new Object[]{1L, new BigDecimal("10000"), BigDecimal.ZERO});
      balances.add(new Object[]{2L, new BigDecimal("50000"), BigDecimal.ZERO});

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findByCompanyIdAndCodeStartingWith(COMPANY_ID, "111"))
          .thenReturn(accounts111);
      when(chartOfAccountsRepository.findByCompanyIdAndCodeStartingWith(COMPANY_ID, "112"))
          .thenReturn(accounts112);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      Page<AccountContributionDTO> result = service.getAccountsForLine(
          REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Should throw exception for calculated line")
    void shouldThrowExceptionForCalculatedLine() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, null, true);
      mapping.setFormula("110+120");
      Pageable pageable = PageRequest.of(0, 10);

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getAccountsForLine(REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable));
    }

    @Test
    @DisplayName("Should throw exception when mapping not found")
    void shouldThrowExceptionWhenMappingNotFound() {
      // Given
      Pageable pageable = PageRequest.of(0, 10);

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getAccountsForLine(REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable));
    }

    @Test
    @DisplayName("Should throw exception when period not found")
    void shouldThrowExceptionWhenPeriodNotFound() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "111*", false);
      Pageable pageable = PageRequest.of(0, 10);

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getAccountsForLine(REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable));
    }

    @Test
    @DisplayName("Should return empty page when no accounts match")
    void shouldReturnEmptyPageWhenNoAccountsMatch() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "999*", false);
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findByCompanyIdAndCodeStartingWith(COMPANY_ID, "999"))
          .thenReturn(List.of());

      // When
      Page<AccountContributionDTO> result = service.getAccountsForLine(
          REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(0, result.getTotalElements());
      assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("Should apply sign modifier to contribution amount")
    void shouldApplySignModifierToContributionAmount() {
      // Given
      ReportMapping mapping = createMapping(LINE_CODE, "111*", false);
      mapping.setSignModifier(-1); // Negate the values
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      List<ChartOfAccount> accounts = List.of(
          createAccount(1L, "1111", "Cash on hand"));

      List<Object[]> balances = new ArrayList<>();
      balances.add(new Object[]{1L, new BigDecimal("10000"), BigDecimal.ZERO});

      when(reportMappingRepository.findCurrentByCompanyAndReportTypeAndLineCode(
          COMPANY_ID, REPORT_TYPE, LINE_CODE))
          .thenReturn(Optional.of(mapping));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findByCompanyIdAndCodeStartingWith(COMPANY_ID, "111"))
          .thenReturn(accounts);
      when(voucherLineRepository.calculateOpeningBalances(eq(COMPANY_ID), any(LocalDate.class)))
          .thenReturn(balances);

      // When
      Page<AccountContributionDTO> result = service.getAccountsForLine(
          REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertFalse(result.getContent().isEmpty());
      // Contribution should be negated: 10000 becomes -10000
      assertEquals(new BigDecimal("-10000"), result.getContent().get(0).getContributionAmount());
    }
  }

  @Nested
  @DisplayName("Get Vouchers for Account")
  class GetVouchersForAccountTests {

    @Test
    @DisplayName("Should return vouchers for account in period")
    void shouldReturnVouchersForAccountInPeriod() {
      // Given
      String accountCode = "1111";
      ChartOfAccount account = createAccount(1L, accountCode, "Cash on hand");
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      UUID voucherId1 = UUID.randomUUID();
      UUID voucherId2 = UUID.randomUUID();

      Voucher voucher1 = createVoucher(voucherId1, "VCH-001", LocalDate.of(2024, 6, 15), "posted");
      Voucher voucher2 = createVoucher(voucherId2, "VCH-002", LocalDate.of(2024, 7, 20), "posted");

      List<VoucherLine> lines = List.of(
          createVoucherLine(voucherId1, 1L, new BigDecimal("5000"), BigDecimal.ZERO),
          createVoucherLine(voucherId2, 1L, BigDecimal.ZERO, new BigDecimal("3000")));

      when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, accountCode))
          .thenReturn(Optional.of(account));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, 1L))
          .thenReturn(lines);
      when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(voucher1));
      when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(voucher2));

      // When
      Page<VoucherSummaryDTO> result = service.getVouchersForAccount(accountCode, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Should filter out draft vouchers")
    void shouldFilterOutDraftVouchers() {
      // Given
      String accountCode = "1111";
      ChartOfAccount account = createAccount(1L, accountCode, "Cash on hand");
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 10);

      UUID voucherId1 = UUID.randomUUID();
      UUID voucherId2 = UUID.randomUUID();

      Voucher postedVoucher = createVoucher(voucherId1, "VCH-001", LocalDate.of(2024, 6, 15), "posted");
      Voucher draftVoucher = createVoucher(voucherId2, "VCH-002", LocalDate.of(2024, 7, 20), "draft");

      List<VoucherLine> lines = List.of(
          createVoucherLine(voucherId1, 1L, new BigDecimal("5000"), BigDecimal.ZERO),
          createVoucherLine(voucherId2, 1L, BigDecimal.ZERO, new BigDecimal("3000")));

      when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, accountCode))
          .thenReturn(Optional.of(account));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, 1L))
          .thenReturn(lines);
      when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(postedVoucher));
      when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(draftVoucher));

      // When
      Page<VoucherSummaryDTO> result = service.getVouchersForAccount(accountCode, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals("VCH-001", result.getContent().get(0).voucherNumber());
    }

    @Test
    @DisplayName("Should filter out vouchers outside period")
    void shouldFilterOutVouchersOutsidePeriod() {
      // Given
      String accountCode = "1111";
      ChartOfAccount account = createAccount(1L, accountCode, "Cash on hand");
      AccountingPeriodDTO period = createPeriod(); // Jan 1 - Dec 31, 2024
      Pageable pageable = PageRequest.of(0, 10);

      UUID voucherId1 = UUID.randomUUID();
      UUID voucherId2 = UUID.randomUUID();

      Voucher inPeriodVoucher = createVoucher(voucherId1, "VCH-001", LocalDate.of(2024, 6, 15), "posted");
      Voucher outsidePeriodVoucher = createVoucher(voucherId2, "VCH-002", LocalDate.of(2023, 12, 31), "posted");

      List<VoucherLine> lines = List.of(
          createVoucherLine(voucherId1, 1L, new BigDecimal("5000"), BigDecimal.ZERO),
          createVoucherLine(voucherId2, 1L, BigDecimal.ZERO, new BigDecimal("3000")));

      when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, accountCode))
          .thenReturn(Optional.of(account));
      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, 1L))
          .thenReturn(lines);
      when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(inPeriodVoucher));
      when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(outsidePeriodVoucher));

      // When
      Page<VoucherSummaryDTO> result = service.getVouchersForAccount(accountCode, PERIOD_ID, pageable);

      // Then
      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
      assertEquals("VCH-001", result.getContent().get(0).voucherNumber());
    }

    @Test
    @DisplayName("Should throw exception when account not found")
    void shouldThrowExceptionWhenAccountNotFound() {
      // Given
      Pageable pageable = PageRequest.of(0, 10);

      when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "9999"))
          .thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getVouchersForAccount("9999", PERIOD_ID, pageable));
    }
  }

  @Nested
  @DisplayName("Get Voucher Detail")
  class GetVoucherDetailTests {

    @Test
    @DisplayName("Should return voucher detail with lines")
    void shouldReturnVoucherDetailWithLines() {
      // Given
      UUID voucherId = UUID.randomUUID();
      Voucher voucher = createVoucher(voucherId, "VCH-001", LocalDate.of(2024, 6, 15), "posted");

      ChartOfAccount cashAccount = createAccount(1L, "1111", "Cash");
      ChartOfAccount revenueAccount = createAccount(2L, "5111", "Revenue");

      List<VoucherLine> lines = List.of(
          createVoucherLine(voucherId, 1L, new BigDecimal("10000"), BigDecimal.ZERO),
          createVoucherLine(voucherId, 2L, BigDecimal.ZERO, new BigDecimal("10000")));
      lines.get(0).setLineNumber(1);
      lines.get(1).setLineNumber(2);

      when(voucherRepository.findById(voucherId)).thenReturn(Optional.of(voucher));
      when(voucherLineRepository.findByCompanyIdAndVoucherIdOrderByLineNumberAsc(COMPANY_ID, voucherId))
          .thenReturn(lines);
      when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(cashAccount));
      when(chartOfAccountsRepository.findById(2L)).thenReturn(Optional.of(revenueAccount));

      // When
      VoucherDetailDTO result = service.getVoucherDetail(voucherId);

      // Then
      assertNotNull(result);
      assertEquals(voucherId, result.voucherId());
      assertEquals("VCH-001", result.voucherNumber());
      assertEquals(2, result.lines().size());

      // Verify line details
      assertEquals("1111", result.lines().get(0).accountCode());
      assertEquals("5111", result.lines().get(1).accountCode());
    }

    @Test
    @DisplayName("Should throw exception when voucher not found")
    void shouldThrowExceptionWhenVoucherNotFound() {
      // Given
      UUID voucherId = UUID.randomUUID();

      when(voucherRepository.findById(voucherId)).thenReturn(Optional.empty());

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getVoucherDetail(voucherId));
    }

    @Test
    @DisplayName("Should throw exception when voucher belongs to different company")
    void shouldThrowExceptionWhenVoucherBelongsToDifferentCompany() {
      // Given
      UUID voucherId = UUID.randomUUID();
      Voucher voucher = createVoucher(voucherId, "VCH-001", LocalDate.of(2024, 6, 15), "posted");
      voucher.setCompanyId(999L); // Different company

      when(voucherRepository.findById(voucherId)).thenReturn(Optional.of(voucher));

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getVoucherDetail(voucherId));
    }
  }

  @Nested
  @DisplayName("Company Context Validation")
  class CompanyContextTests {

    @Test
    @DisplayName("Should throw exception when company context missing for accounts query")
    void shouldThrowExceptionWhenCompanyContextMissingForAccountsQuery() {
      // Given
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);
      Pageable pageable = PageRequest.of(0, 10);

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getAccountsForLine(REPORT_TYPE, LINE_CODE, PERIOD_ID, pageable));
    }

    @Test
    @DisplayName("Should throw exception when company context missing for vouchers query")
    void shouldThrowExceptionWhenCompanyContextMissingForVouchersQuery() {
      // Given
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);
      Pageable pageable = PageRequest.of(0, 10);

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getVouchersForAccount("1111", PERIOD_ID, pageable));
    }

    @Test
    @DisplayName("Should throw exception when company context missing for voucher detail")
    void shouldThrowExceptionWhenCompanyContextMissingForVoucherDetail() {
      // Given
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);
      UUID voucherId = UUID.randomUUID();

      // When/Then
      assertThrows(ResponseStatusException.class, () ->
          service.getVoucherDetail(voucherId));
    }
  }
}
