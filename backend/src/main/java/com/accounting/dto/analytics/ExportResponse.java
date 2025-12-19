package com.accounting.dto.analytics;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Analytics dashboard export response")
public record ExportResponse(
        @Schema(description = "Unique export job ID", example = "exp-550e8400-e29b-41d4") String jobId,
        @Schema(
                        description = "Export job status",
                        example = "COMPLETED",
                        allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"})
                String status,
        @Schema(description = "Download URL for completed export", example = "/api/v1/analytics/dashboard/export/exp-123")
                String downloadUrl,
        @Schema(description = "Expiration timestamp for the download URL") LocalDateTime expiresAt,
        @Schema(description = "Export metadata including company info and watermark") ExportMetadata metadata) {

    public enum ExportStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
