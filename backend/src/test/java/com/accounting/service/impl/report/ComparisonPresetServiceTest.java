package com.accounting.service.impl.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
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
import com.accounting.dto.report.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.security.CompanyContext;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComparisonPresetServiceTest {

  @Mock
  private AccountingPeriodRepository periodRepository;

  private ComparisonPresetServiceImpl service;
  private MockedStatic<CompanyContext> companyContextMock;

  private static final Long COMPANY_ID = 1L;

  @BeforeEach
  void setUp() {
    service = new ComparisonPresetServiceImpl(periodRepository);

    companyContextMock = mockStatic(CompanyContext.class);
    companyContextMock.when(CompanyContext::getCompanyId).thenReturn(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    companyContextMock.close();
  }

  private AccountingPeriod createPeriod(UUID id, String name, int year, int month, Integer periodNumber) {
    AccountingPeriod period = new AccountingPeriod();
    period.setId(id);
    period.setPeriodName(name);
    period.setStartDate(LocalDate.of(year, month, 1));
    period.setEndDate(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    period.setStatus(PeriodStatus.CLOSED);
    period.setFiscalYear(year);
    period.setPeriodNumber(periodNumber);
    period.setCompanyId(COMPANY_ID);
    period.setCreatedAt(Instant.now());
    period.setUpdatedAt(Instant.now());
    return period;
  }

  private AccountingPeriod createQuarterlyPeriod(UUID id, String name, int year, int quarter) {
    int startMonth = (quarter - 1) * 3 + 1;
    AccountingPeriod period = new AccountingPeriod();
    period.setId(id);
    period.setPeriodName(name);
    period.setStartDate(LocalDate.of(year, startMonth, 1));
    period.setEndDate(LocalDate.of(year, startMonth, 1).plusMonths(3).minusDays(1));
    period.setStatus(PeriodStatus.CLOSED);
    period.setFiscalYear(year);
    period.setPeriodNumber(quarter);
    period.setCompanyId(COMPANY_ID);
    period.setCreatedAt(Instant.now());
    period.setUpdatedAt(Instant.now());
    return period;
  }

  @Nested
  @DisplayName("YoY Preset Generation Tests")
  class YoYPresetGenerationTests {

    @Test
    @DisplayName("Should return same month from prior years")
    void shouldReturnSameMonthFromPriorYears() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorYear1Id = UUID.randomUUID();
      UUID priorYear2Id = UUID.randomUUID();
      UUID priorYear3Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod1 = createPeriod(priorYear1Id, "Mar 2023", 2023, 3, 3);
      AccountingPeriod priorPeriod2 = createPeriod(priorYear2Id, "Mar 2022", 2022, 3, 3);
      AccountingPeriod priorPeriod3 = createPeriod(priorYear3Id, "Mar 2021", 2021, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(priorPeriod1));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2022, 3))
          .thenReturn(Optional.of(priorPeriod2));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2021, 3))
          .thenReturn(Optional.of(priorPeriod3));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "YOY");

      assertNotNull(result);
      assertEquals(3, result.size());
      assertEquals(priorYear1Id, result.get(0).getId());
      assertEquals(priorYear2Id, result.get(1).getId());
      assertEquals(priorYear3Id, result.get(2).getId());
    }

    @Test
    @DisplayName("Should return fewer periods when prior years do not exist")
    void shouldReturnFewerPeriodsWhenPriorYearsDoNotExist() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorYear1Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod1 = createPeriod(priorYear1Id, "Mar 2023", 2023, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(priorPeriod1));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2022, 3))
          .thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2021, 3))
          .thenReturn(Optional.empty());

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "YOY");

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(priorYear1Id, result.get(0).getId());
    }

    @Test
    @DisplayName("Should fallback to date-based search when period number is null")
    void shouldFallbackToDateBasedSearchWhenPeriodNumberIsNull() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorYear1Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, null);
      AccountingPeriod priorPeriod1 = createPeriod(priorYear1Id, "Mar 2023", 2023, 3, null);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndStartDateBetweenOrderByStartDate(
          eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of(priorPeriod1));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "YOY");

      assertNotNull(result);
      assertFalse(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("MoM Preset Generation Tests")
  class MoMPresetGenerationTests {

    @Test
    @DisplayName("Should return consecutive months")
    void shouldReturnConsecutiveMonths() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevMonth1Id = UUID.randomUUID();
      UUID prevMonth2Id = UUID.randomUUID();
      UUID prevMonth3Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Apr 2024", 2024, 4, 4);
      AccountingPeriod prevPeriod1 = createPeriod(prevMonth1Id, "Mar 2024", 2024, 3, 3);
      AccountingPeriod prevPeriod2 = createPeriod(prevMonth2Id, "Feb 2024", 2024, 2, 2);
      AccountingPeriod prevPeriod3 = createPeriod(prevMonth3Id, "Jan 2024", 2024, 1, 1);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndStartDateBetweenOrderByStartDate(
          eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of(prevPeriod1))
          .thenReturn(List.of(prevPeriod2))
          .thenReturn(List.of(prevPeriod3));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "MOM");

      assertNotNull(result);
      assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Should handle year boundary crossing")
    void shouldHandleYearBoundaryCrossing() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevMonth1Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Jan 2024", 2024, 1, 1);
      AccountingPeriod prevPeriod1 = createPeriod(prevMonth1Id, "Dec 2023", 2023, 12, 12);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndStartDateBetweenOrderByStartDate(
          eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of(prevPeriod1));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "MOM");

      assertNotNull(result);
      assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Should skip missing months gracefully")
    void shouldSkipMissingMonthsGracefully() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevMonth2Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Apr 2024", 2024, 4, 4);
      AccountingPeriod prevPeriod2 = createPeriod(prevMonth2Id, "Feb 2024", 2024, 2, 2);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndStartDateBetweenOrderByStartDate(
          eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of())
          .thenReturn(List.of(prevPeriod2))
          .thenReturn(List.of());

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "MOM");

      assertNotNull(result);
      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayName("Quarterly Preset Generation Tests")
  class QuarterlyPresetGenerationTests {

    @Test
    @DisplayName("Should return previous quarters")
    void shouldReturnPreviousQuarters() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevQ1Id = UUID.randomUUID();
      UUID prevQ2Id = UUID.randomUUID();
      UUID prevQ3Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createQuarterlyPeriod(currentPeriodId, "Q4 2024", 2024, 4);
      AccountingPeriod prevQ1 = createQuarterlyPeriod(prevQ1Id, "Q3 2024", 2024, 3);
      AccountingPeriod prevQ2 = createQuarterlyPeriod(prevQ2Id, "Q2 2024", 2024, 2);
      AccountingPeriod prevQ3 = createQuarterlyPeriod(prevQ3Id, "Q1 2024", 2024, 1);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2024, 3))
          .thenReturn(Optional.of(prevQ1));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2024, 2))
          .thenReturn(Optional.of(prevQ2));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2024, 1))
          .thenReturn(Optional.of(prevQ3));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "QUARTERLY");

      assertNotNull(result);
      assertEquals(3, result.size());
      assertEquals(prevQ1Id, result.get(0).getId());
      assertEquals(prevQ2Id, result.get(1).getId());
      assertEquals(prevQ3Id, result.get(2).getId());
    }

    @Test
    @DisplayName("Should handle quarter wrapping to previous year")
    void shouldHandleQuarterWrappingToPreviousYear() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevQ1Id = UUID.randomUUID();
      UUID prevQ2Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createQuarterlyPeriod(currentPeriodId, "Q1 2024", 2024, 1);
      AccountingPeriod prevQ1 = createQuarterlyPeriod(prevQ1Id, "Q4 2023", 2023, 4);
      AccountingPeriod prevQ2 = createQuarterlyPeriod(prevQ2Id, "Q3 2023", 2023, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 4))
          .thenReturn(Optional.of(prevQ1));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(prevQ2));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 2))
          .thenReturn(Optional.empty());

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "QUARTERLY");

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should fallback to date-based search when period number is invalid")
    void shouldFallbackToDateBasedSearchWhenPeriodNumberIsInvalid() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID prevPeriodId = UUID.randomUUID();

      AccountingPeriod currentPeriod = createQuarterlyPeriod(currentPeriodId, "Q4 2024", 2024, 4);
      currentPeriod.setPeriodNumber(10);

      AccountingPeriod prevPeriod = createQuarterlyPeriod(prevPeriodId, "Q3 2024", 2024, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndStartDateBetweenOrderByStartDate(
          eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of(prevPeriod));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "QUARTERLY");

      assertNotNull(result);
    }
  }

  @Nested
  @DisplayName("Handling Missing Periods Tests")
  class HandlingMissingPeriodsTests {

    @Test
    @DisplayName("Should return empty list when no prior periods exist")
    void shouldReturnEmptyListWhenNoPriorPeriodsExist() {
      UUID currentPeriodId = UUID.randomUUID();
      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Jan 2024", 2024, 1, 1);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(eq(COMPANY_ID), anyInt(), anyInt()))
          .thenReturn(Optional.empty());

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "YOY");

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should skip gaps in period sequences")
    void shouldSkipGapsInPeriodSequences() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorYear3Id = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod3 = createPeriod(priorYear3Id, "Mar 2021", 2021, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2022, 3))
          .thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2021, 3))
          .thenReturn(Optional.of(priorPeriod3));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "YOY");

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(priorYear3Id, result.get(0).getId());
    }
  }

  @Nested
  @DisplayName("GetAvailablePeriods Tests")
  class GetAvailablePeriodsTests {

    @Test
    @DisplayName("Should return all periods sorted by start date descending")
    void shouldReturnAllPeriodsSortedByStartDateDescending() {
      UUID period1Id = UUID.randomUUID();
      UUID period2Id = UUID.randomUUID();
      UUID period3Id = UUID.randomUUID();

      AccountingPeriod period1 = createPeriod(period1Id, "Jan 2024", 2024, 1, 1);
      AccountingPeriod period2 = createPeriod(period2Id, "Feb 2024", 2024, 2, 2);
      AccountingPeriod period3 = createPeriod(period3Id, "Mar 2024", 2024, 3, 3);

      when(periodRepository.findByCompanyId(COMPANY_ID))
          .thenReturn(List.of(period1, period2, period3));

      List<PeriodSummaryDTO> result = service.getAvailablePeriods();

      assertNotNull(result);
      assertEquals(3, result.size());
      assertEquals(period3Id, result.get(0).periodId());
      assertEquals(period2Id, result.get(1).periodId());
      assertEquals(period1Id, result.get(2).periodId());
    }

    @Test
    @DisplayName("Should return empty list when no periods exist")
    void shouldReturnEmptyListWhenNoPeriodsExist() {
      when(periodRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of());

      List<PeriodSummaryDTO> result = service.getAvailablePeriods();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should include period status in summary")
    void shouldIncludePeriodStatusInSummary() {
      UUID periodId = UUID.randomUUID();
      AccountingPeriod period = createPeriod(periodId, "Jan 2024", 2024, 1, 1);
      period.setStatus(PeriodStatus.OPEN);

      when(periodRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(period));

      List<PeriodSummaryDTO> result = service.getAvailablePeriods();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("OPEN", result.get(0).status());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should throw exception for invalid mode")
    void shouldThrowExceptionForInvalidMode() {
      UUID currentPeriodId = UUID.randomUUID();
      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Jan 2024", 2024, 1, 1);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));

      assertThrows(ResponseStatusException.class, () ->
          service.getSuggestedPeriods(currentPeriodId, "INVALID_MODE"));
    }

    @Test
    @DisplayName("Should throw exception when period not found")
    void shouldThrowExceptionWhenPeriodNotFound() {
      UUID periodId = UUID.randomUUID();
      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, periodId))
          .thenReturn(Optional.empty());

      assertThrows(ResponseStatusException.class, () ->
          service.getSuggestedPeriods(periodId, "YOY"));
    }

    @Test
    @DisplayName("Should throw exception when company context missing")
    void shouldThrowExceptionWhenCompanyContextMissing() {
      companyContextMock.when(CompanyContext::getCompanyId).thenReturn(null);

      assertThrows(ResponseStatusException.class, () ->
          service.getSuggestedPeriods(UUID.randomUUID(), "YOY"));
    }

    @Test
    @DisplayName("CUSTOM mode should return empty list")
    void customModeShouldReturnEmptyList() {
      UUID currentPeriodId = UUID.randomUUID();
      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Jan 2024", 2024, 1, 1);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "CUSTOM");

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle case-insensitive mode")
    void shouldHandleCaseInsensitiveMode() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorPeriodId = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod = createPeriod(priorPeriodId, "Mar 2023", 2023, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(priorPeriod));

      List<AccountingPeriodDTO> result = service.getSuggestedPeriods(currentPeriodId, "yoy");

      assertNotNull(result);
      assertFalse(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("GetSuggestedPeriodIds Tests")
  class GetSuggestedPeriodIdsTests {

    @Test
    @DisplayName("Should return period IDs only")
    void shouldReturnPeriodIdsOnly() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorPeriodId = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod = createPeriod(priorPeriodId, "Mar 2023", 2023, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(priorPeriod));

      List<UUID> result = service.getSuggestedPeriodIds(currentPeriodId, "YOY", 3);

      assertNotNull(result);
      assertTrue(result.contains(priorPeriodId));
    }

    @Test
    @DisplayName("Should respect maxPeriods limit")
    void shouldRespectMaxPeriodsLimit() {
      UUID currentPeriodId = UUID.randomUUID();
      UUID priorPeriodId = UUID.randomUUID();

      AccountingPeriod currentPeriod = createPeriod(currentPeriodId, "Mar 2024", 2024, 3, 3);
      AccountingPeriod priorPeriod = createPeriod(priorPeriodId, "Mar 2023", 2023, 3, 3);

      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, currentPeriodId))
          .thenReturn(Optional.of(currentPeriod));
      when(periodRepository.findByCompanyIdAndFiscalYearAndPeriodNumber(COMPANY_ID, 2023, 3))
          .thenReturn(Optional.of(priorPeriod));

      List<UUID> result = service.getSuggestedPeriodIds(currentPeriodId, "YOY", 1);

      assertNotNull(result);
      assertEquals(1, result.size());
    }
  }
}
