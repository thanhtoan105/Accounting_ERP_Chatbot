package com.accounting.dto.analytics;

import jakarta.validation.constraints.NotBlank;

public record ExportRejectRequest(@NotBlank(message = "Rejection reason is required") String reason) {}
