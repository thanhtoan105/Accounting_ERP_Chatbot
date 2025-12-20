package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.DrillDownResponseDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.PeriodStatus;
import com.accounting.enums.AmountType;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.report.TrialBalancePdfExportService;
import com.accounting.service.report.TrialBalanceSnapshotService;

/**
 * Unit tests for TrialBalanceServiceImpl drill-down methods.
 * Tests Story 7.1 drill-down functionality (AC #4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrialBalanceServiceImplDrillDownTest {

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Mock
  private VoucherLineRepository voucherLineRepository;

  @Mock
  private PeriodManagementService periodManagementService;

  @Mock
  private CompanyService companyService;

  @Mock
  private TrialBalancePdfExportService trialBalancePdfExportService;

  @Mock
  private TrialBalanceSnapshotService trialBalanceSnapshotService;

  private TrialBalanceServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;

  private static final Long COMPANY_ID = 1L;
  private static final UUID PERIOD_ID = UUID.randomUUID();
  private static final Long ACCOUNT_ID = 1111L;

  @BeforeEach
  void setUp() {
    service = new TrialBalanceServiceImpl(
        voucherLineRepository,
        chartOfAccountsRepository,
        periodManagementService,
        companyService,
        trialBalancePdfExportService,
        trialBalanceSnapshotService);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
  }

  private AccountingPeriodDTO createPeriod() {
    AccountingPeriodDTO period = new AccountingPeriodDTO();
    period.setId(PERIOD_ID);
    period.setPeriodName("December 2024");
    period.setStartDate(LocalDate.of(2024, 12, 1));
    period.setEndDate(LocalDate.of(2024, 12, 31));
    period.setStatus(PeriodStatus.OPEN);
    period.setFiscalYear(2024);
    period.setPeriodNumber(12);
    return period;
  }

  private ChartOfAccount createAccount() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(ACCOUNT_ID);
    account.setCode("1111");
    account.setName("Cash on hand");
    account.setCompanyId(COMPANY_ID);
    account.setNormalSide("DEBIT");
    return account;
  }

  @Nested
  @DisplayName("Drill-Down Vouchers")
  class GetDrillDownVouchersTests {

    @Test
    @DisplayName("Should return period debit vouchers")
    void shouldReturnPeriodDebitVouchers() {
      AccountingPeriodDTO period = createPeriod();
      ChartOfAccount account = createAccount();
      Pageable pageable = PageRequest.of(0, 20);

      UUID voucherId = UUID.randomUUID();
      Object[] voucherRow = new Object[] {
          voucherId, "VCH-001", LocalDate.of(2024, 12, 15),
          "Test voucher", new BigDecimal("10000"), BigDecimal.ZERO,
          "general", "posted"
      };

      List<Object[]> voucherList = new ArrayList<>();
      voucherList.add(voucherRow);
      Page<Object[]> page = new PageImpl<>(voucherList, pageable, 1);

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
      when(voucherLineRepository.findPeriodDebitVouchers(
          eq(COMPANY_ID), eq(ACCOUNT_ID), eq(PERIOD_ID), any(Pageable.class)))
          .thenReturn(page);
      when(voucherLineRepository.sumPeriodDebit(COMPANY_ID, ACCOUNT_ID, PERIOD_ID))
          .thenReturn(new BigDecimal("10000"));

      DrillDownResponseDTO result = service.getDrillDownVouchers(
          PERIOD_ID, ACCOUNT_ID, AmountType.PERIOD_DEBIT, pageable);

      assertNotNull(result);
      assertEquals(1, result.getVouchers().size());
      assertEquals(1, result.getVoucherCount());
      assertEquals(new BigDecimal("10000"), result.getTotalAmount());

      var voucher = result.getVouchers().get(0);
      assertEquals(voucherId, voucher.getId());
      assertEquals("VCH-001", voucher.getVoucherNumber());
      assertEquals(new BigDecimal("10000"), voucher.getDebit());
    }

    @Test
    @DisplayName("Should throw exception when period not found")
    void shouldThrowExceptionWhenPeriodNotFound() {
      Pageable pageable = PageRequest.of(0, 20);

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.empty());

      assertThrows(ResponseStatusException.class, () ->
          service.getDrillDownVouchers(PERIOD_ID, ACCOUNT_ID, AmountType.PERIOD_DEBIT, pageable));
    }

    @Test
    @DisplayName("Should throw exception when account not found")
    void shouldThrowExceptionWhenAccountNotFound() {
      AccountingPeriodDTO period = createPeriod();
      Pageable pageable = PageRequest.of(0, 20);

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

      assertThrows(ResponseStatusException.class, () ->
          service.getDrillDownVouchers(PERIOD_ID, ACCOUNT_ID, AmountType.PERIOD_DEBIT, pageable));
    }

    @Test
    @DisplayName("Should throw exception when company context missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);
      Pageable pageable = PageRequest.of(0, 20);

      assertThrows(ResponseStatusException.class, () ->
          service.getDrillDownVouchers(PERIOD_ID, ACCOUNT_ID, AmountType.PERIOD_DEBIT, pageable));
    }

    @Test
    @DisplayName("Should support pagination")
    void shouldSupportPagination() {
      AccountingPeriodDTO period = createPeriod();
      ChartOfAccount account = createAccount();
      Pageable pageable = PageRequest.of(1, 10);

      List<Object[]> emptyList = Collections.emptyList();
      Page<Object[]> page = new PageImpl<>(emptyList, pageable, 25);

      when(periodManagementService.getPeriodById(PERIOD_ID)).thenReturn(Optional.of(period));
      when(chartOfAccountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
      when(voucherLineRepository.findPeriodDebitVouchers(
          eq(COMPANY_ID), eq(ACCOUNT_ID), eq(PERIOD_ID), any(Pageable.class)))
          .thenReturn(page);
      when(voucherLineRepository.sumPeriodDebit(COMPANY_ID, ACCOUNT_ID, PERIOD_ID))
          .thenReturn(BigDecimal.ZERO);

      DrillDownResponseDTO result = service.getDrillDownVouchers(
          PERIOD_ID, ACCOUNT_ID, AmountType.PERIOD_DEBIT, pageable);

      assertNotNull(result);
      assertEquals(1, result.getPage());
      assertEquals(10, result.getSize());
      assertEquals(25, result.getTotal());
      assertTrue(result.isHasNext());
    }
  }
}
