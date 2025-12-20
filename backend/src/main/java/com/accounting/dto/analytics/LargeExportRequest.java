package com.accounting.dto.analytics;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LargeExportRequest(
        @NotBlank(message = "Export type is required") String exportType,
        @NotNull(message = "Row count is required") @Min(value = 1, message = "Row count must be positive") Integer rowCount,
        String queryParameters) {}
