package com.accounting.dto.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MultiPeriodLineDTO(
    String lineCode,
    String lineName,
    String lineNameEnglish,
    int level,
    boolean isCalculated,
    Map<UUID, BigDecimal> periodValues,
    List<VarianceDTO> variances,
    List<Double> sparklineData,
    boolean isMaterial,
    boolean hasDrillDown) {}
