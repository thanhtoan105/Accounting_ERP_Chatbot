package com.accounting.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record VarianceDTO(
    UUID fromPeriodId,
    UUID toPeriodId,
    BigDecimal absoluteVariance,
    Double percentVariance,
    String direction) {}
