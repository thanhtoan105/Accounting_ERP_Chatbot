package com.accounting.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to export analytics dashboard data")
public record ExportRequest(
        @Schema(description = "Accounting period ID", example = "6d472567-5342-40ac-9555-0be8672bc7b9")
                @NotNull(message = "Period ID is required")
                java.util.UUID periodId,
        @Schema(
                        description = "Dashboard type to export",
                        example = "FINANCIAL_OVERVIEW",
                        allowableValues = {"FINANCIAL_OVERVIEW", "AR_AP_AGING", "CASH_FLOW", "PERIOD_SUMMARY"})
                @NotBlank(message = "Dashboard type is required")
                @Pattern(
                        regexp = "FINANCIAL_OVERVIEW|AR_AP_AGING|CASH_FLOW|PERIOD_SUMMARY",
                        message =
                                "Dashboard type must be one of: FINANCIAL_OVERVIEW, AR_AP_AGING, CASH_FLOW, PERIOD_SUMMARY")
                String dashboardType,
        @Schema(description = "Export file format", example = "EXCEL", allowableValues = {"EXCEL", "PDF"})
                @NotBlank(message = "Format is required")
                @Pattern(regexp = "EXCEL|PDF", message = "Format must be either EXCEL or PDF")
                String format) {}
