package com.accounting.dto.report;

import java.time.LocalDate;
import java.util.UUID;

public record PeriodSummaryDTO(
    UUID periodId,
    String periodName,
    Integer fiscalYear,
    Integer periodNumber,
    LocalDate startDate,
    LocalDate endDate,
    String status) {}
