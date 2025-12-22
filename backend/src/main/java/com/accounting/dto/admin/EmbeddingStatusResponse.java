package com.accounting.dto.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record EmbeddingStatusResponse(long total, long embedded, long pending, BigDecimal percentage) {

  public static EmbeddingStatusResponse of(long total, long embedded) {
    long pending = total - embedded;
    BigDecimal percentage =
        total > 0
            ? BigDecimal.valueOf(embedded)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    return new EmbeddingStatusResponse(total, embedded, pending, percentage);
  }
}
