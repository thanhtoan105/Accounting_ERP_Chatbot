package com.accounting.dto;

import java.util.List;
import java.util.UUID;

public record ImportResultDTO(
        int successCount,
        int skippedCount,
        int errorCount,
        List<ImportRowErrorDTO> errors,
        UUID errorReportId) {
}
