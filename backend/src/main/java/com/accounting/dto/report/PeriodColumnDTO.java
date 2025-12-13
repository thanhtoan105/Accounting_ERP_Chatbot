package com.accounting.dto.report;

import java.time.LocalDate;
import java.util.UUID;

public record PeriodColumnDTO(
    UUID periodId,
    String periodName,
    LocalDate startDate,
    LocalDate endDate,
    String fiscalYear,
    boolean isDraft) {}
