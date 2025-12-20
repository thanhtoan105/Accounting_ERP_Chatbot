package com.accounting.dto.report;

import java.time.Instant;
import java.util.List;

public record MultiPeriodReportDTO(
    String reportType,
    String reportName,
    Long companyId,
    String companyName,
    List<PeriodColumnDTO> periods,
    List<MultiPeriodLineDTO> lines,
    ComparisonSettingsDTO settings,
    Instant generatedAt,
    boolean hasDraftPeriod) {}
