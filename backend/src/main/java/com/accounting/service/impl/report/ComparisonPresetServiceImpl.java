package com.accounting.service.impl.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ComparisonPresetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonPresetServiceImpl implements ComparisonPresetService {

  private static final int DEFAULT_MAX_PERIODS = 3;

  private final AccountingPeriodRepository periodRepository;

  @Override
  public List<AccountingPeriodDTO> getSuggestedPeriods(UUID currentPeriodId, String mode) {
    Long companyId = requireCompanyId();

    AccountingPeriod currentPeriod = findPeriodOrThrow(companyId, currentPeriodId);

    return switch (mode.toUpperCase()) {
      case "YOY" -> getYearOverYearPeriods(currentPeriod, companyId, DEFAULT_MAX_PERIODS);
      case "MOM" -> getMonthOverMonthPeriods(currentPeriod, companyId, DEFAULT_MAX_PERIODS);
      case "QUARTERLY" -> getQuarterlyPeriods(currentPeriod, companyId, DEFAULT_MAX_PERIODS);
      case "CUSTOM" -> List.of();
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Invalid comparison mode: " + mode);
    };
  }

  @Override
  public List<UUID> getSuggestedPeriodIds(UUID basePeriodId, String mode, int maxPeriods) {
    Long companyId = requireCompanyId();

    AccountingPeriod basePeriod = findPeriodOrThrow(companyId, basePeriodId);
    int limit = Math.min(Math.max(maxPeriods, 1), 10);

    List<AccountingPeriodDTO> periods =
        switch (mode.toUpperCase()) {
          case "YOY" -> getYearOverYearPeriods(basePeriod, companyId, limit);
          case "MOM" -> getMonthOverMonthPeriods(basePeriod, companyId, limit);
          case "QUARTERLY" -> getQuarterlyPeriods(basePeriod, companyId, limit);
          case "CUSTOM" -> List.of();
          default ->
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "Invalid comparison mode: " + mode);
        };

    return periods.stream().map(AccountingPeriodDTO::getId).toList();
  }

  @Override
  public List<PeriodSummaryDTO> getAvailablePeriods() {
    Long companyId = requireCompanyId();

    return periodRepository.findByCompanyId(companyId).stream()
        .sorted(Comparator.comparing(AccountingPeriod::getStartDate).reversed())
        .map(this::toSummaryDTO)
        .toList();
  }

  private List<AccountingPeriodDTO> getYearOverYearPeriods(
      AccountingPeriod current, Long companyId, int maxPeriods) {
    List<AccountingPeriodDTO> results = new ArrayList<>();
    Integer currentYear = current.getFiscalYear();
    Integer currentPeriodNum = current.getPeriodNumber();

    if (currentYear == null || currentPeriodNum == null) {
      return getYearOverYearPeriodsByDate(current, companyId, maxPeriods);
    }

    for (int yearsBack = 1; yearsBack <= maxPeriods; yearsBack++) {
      int targetYear = currentYear - yearsBack;
      periodRepository
          .findByCompanyIdAndFiscalYearAndPeriodNumber(companyId, targetYear, currentPeriodNum)
          .ifPresent(p -> results.add(toDTO(p)));
    }

    return results;
  }

  private List<AccountingPeriodDTO> getYearOverYearPeriodsByDate(
      AccountingPeriod current, Long companyId, int maxPeriods) {
    List<AccountingPeriodDTO> results = new ArrayList<>();
    LocalDate currentStart = current.getStartDate();

    for (int yearsBack = 1; yearsBack <= maxPeriods; yearsBack++) {
      LocalDate targetStart = currentStart.minusYears(yearsBack);

      periodRepository
          .findByCompanyIdAndStartDateBetweenOrderByStartDate(
              companyId, targetStart.minusDays(5), targetStart.plusDays(5))
          .stream()
          .findFirst()
          .ifPresent(p -> results.add(toDTO(p)));
    }

    return results;
  }

  private List<AccountingPeriodDTO> getMonthOverMonthPeriods(
      AccountingPeriod current, Long companyId, int maxPeriods) {
    List<AccountingPeriodDTO> results = new ArrayList<>();
    LocalDate currentStart = current.getStartDate();

    for (int monthsBack = 1; monthsBack <= maxPeriods; monthsBack++) {
      LocalDate targetStart = currentStart.minusMonths(monthsBack);

      periodRepository
          .findByCompanyIdAndStartDateBetweenOrderByStartDate(
              companyId, targetStart.minusDays(5), targetStart.plusDays(5))
          .stream()
          .findFirst()
          .ifPresent(p -> results.add(toDTO(p)));
    }

    return results;
  }

  private List<AccountingPeriodDTO> getQuarterlyPeriods(
      AccountingPeriod current, Long companyId, int maxPeriods) {
    List<AccountingPeriodDTO> results = new ArrayList<>();
    Integer currentYear = current.getFiscalYear();
    Integer currentPeriodNum = current.getPeriodNumber();

    if (currentYear != null && currentPeriodNum != null && currentPeriodNum >= 1 && currentPeriodNum <= 4) {
      for (int quartersBack = 1; quartersBack <= maxPeriods; quartersBack++) {
        int targetQuarter = currentPeriodNum - quartersBack;
        int targetYear = currentYear;

        while (targetQuarter <= 0) {
          targetQuarter += 4;
          targetYear--;
        }

        final int finalTargetYear = targetYear;
        final int finalTargetQuarter = targetQuarter;
        periodRepository
            .findByCompanyIdAndFiscalYearAndPeriodNumber(companyId, finalTargetYear, finalTargetQuarter)
            .ifPresent(p -> results.add(toDTO(p)));
      }
    } else {
      return getQuarterlyPeriodsByDate(current, companyId, maxPeriods);
    }

    return results;
  }

  private List<AccountingPeriodDTO> getQuarterlyPeriodsByDate(
      AccountingPeriod current, Long companyId, int maxPeriods) {
    List<AccountingPeriodDTO> results = new ArrayList<>();
    LocalDate currentStart = current.getStartDate();

    for (int quartersBack = 1; quartersBack <= maxPeriods; quartersBack++) {
      LocalDate targetStart = currentStart.minusMonths(quartersBack * 3L);

      periodRepository
          .findByCompanyIdAndStartDateBetweenOrderByStartDate(
              companyId, targetStart.minusDays(5), targetStart.plusDays(5))
          .stream()
          .findFirst()
          .ifPresent(p -> results.add(toDTO(p)));
    }

    return results;
  }

  private AccountingPeriod findPeriodOrThrow(Long companyId, UUID periodId) {
    return periodRepository
        .findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Period not found: " + periodId));
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context required");
    }
    return companyId;
  }

  private AccountingPeriodDTO toDTO(AccountingPeriod period) {
    AccountingPeriodDTO dto = new AccountingPeriodDTO();
    dto.setId(period.getId());
    dto.setCompanyId(period.getCompanyId());
    dto.setFiscalYear(period.getFiscalYear());
    dto.setPeriodNumber(period.getPeriodNumber());
    dto.setPeriodName(period.getPeriodName());
    dto.setStartDate(period.getStartDate());
    dto.setEndDate(period.getEndDate());
    dto.setStatus(period.getStatus());
    dto.setClosedBy(period.getClosedBy());
    dto.setClosedAt(period.getClosedAt());
    dto.setCloseReason(period.getCloseReason());
    dto.setCreatedAt(period.getCreatedAt());
    dto.setUpdatedAt(period.getUpdatedAt());
    dto.setVersion(period.getVersion());
    return dto;
  }

  private PeriodSummaryDTO toSummaryDTO(AccountingPeriod period) {
    return new PeriodSummaryDTO(
        period.getId(),
        period.getPeriodName(),
        period.getFiscalYear(),
        period.getPeriodNumber(),
        period.getStartDate(),
        period.getEndDate(),
        period.getStatus() != null ? period.getStatus().name() : null);
  }
}
